package com.pfc.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfc.shared.ratelimit.RateLimitBucketProvider;
import com.pfc.shared.ratelimit.RateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuração central do Spring Security: cadeia de filtros stateless com
 * autenticação via JWT, cabeçalhos HTTP de proteção e CORS restrito por
 * configuração (nunca {@code *} — ver SECURITY.md > CORS).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            // Health check da plataforma de deploy (Render). Só o /actuator/health
            // é exposto e sem detalhes (ver application.yaml > management).
            "/actuator/health"
    };

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final RateLimitBucketProvider rateLimitBucketProvider;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtService jwtService,
                          CustomUserDetailsService userDetailsService,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          RateLimitBucketProvider rateLimitBucketProvider,
                          ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.rateLimitBucketProvider = rateLimitBucketProvider;
        this.objectMapper = objectMapper;
    }

    /**
     * Registrado como {@code @Bean} (em vez de {@code @Component}) para evitar
     * que o Spring Boot o registre automaticamente como filtro de servlet "solto"
     * — ele deve existir apenas dentro da {@link SecurityFilterChain}, na ordem
     * definida abaixo (antes de {@link UsernamePasswordAuthenticationFilter}).
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtService, userDetailsService);
    }

    /**
     * Mesmo raciocínio do {@link #jwtAuthenticationFilter()}: registrado como
     * {@code @Bean} dentro da cadeia (não {@code @Component}), para que não
     * seja também registrado como filtro de servlet genérico pelo Spring Boot
     * — o que o faria rodar duas vezes por requisição.
     *
     * <p>Posicionado <em>antes</em> de {@link JwtAuthenticationFilter} na
     * cadeia (ver {@link #securityFilterChain}) por dois motivos: (1) precisa
     * proteger as rotas públicas de auth, que nunca alcançam o filtro JWT;
     * (2) requisições que estourarem o limite devem ser rejeitadas o quanto
     * antes — antes de qualquer parsing de credenciais ou autenticação. Por
     * isso ele extrai e valida o JWT diretamente via {@link JwtService} (não
     * depende do {@code SecurityContextHolder} já estar populado).
     */
    @Bean
    public RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter(rateLimitBucketProvider, jwtService, objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    CorsConfigurationSource corsConfigurationSource,
                                                    JwtAuthenticationFilter jwtAuthenticationFilter,
                                                    RateLimitFilter rateLimitFilter) throws Exception {
        http
                // API stateless autenticada por JWT no header Authorization: não há sessão
                // nem cookies de sessão, logo não há superfície para CSRF clássico — desabilitar
                // é a prática padrão recomendada pelo próprio Spring Security para esse cenário.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> headers
                        // X-Content-Type-Options e X-Frame-Options já vêm habilitados por padrão
                        // no Spring Security 6; declarados aqui de forma explícita para deixar a
                        // política visível e documentada (defesa contra MIME sniffing e clickjacking).
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        // HSTS só tem efeito sobre HTTPS (o navegador ignora o header em HTTP),
                        // mas configuramos aqui para já estar ativo quando estiver atrás de TLS em produção.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Roda antes até do filtro JWT: precisa conter força bruta nas rotas
                // públicas de auth (que nunca passam pelo JwtAuthenticationFilter) e
                // rejeitar excessos o quanto antes, sem gastar trabalho de autenticação.
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Não declaramos um {@code DaoAuthenticationProvider} manualmente: com os
     * beans {@link CustomUserDetailsService} e {@link PasswordEncoder} já no
     * contexto, o próprio {@link AuthenticationConfiguration} monta esse
     * provider automaticamente — declará-lo à mão apenas duplicaria a
     * configuração (e o Spring Security alerta sobre isso no startup).
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Origens permitidas vêm de {@code app.cors.allowed-origins} (lista separada por
     * vírgula). O padrão cobre apenas hosts locais de desenvolvimento — em produção
     * a propriedade DEVE ser sobrescrita com o domínio real do frontend; jamais usar
     * {@code "*"} (permitiria que qualquer site fizesse requisições autenticadas em
     * nome do usuário — ver SECURITY.md > CORS).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("#{'${app.cors.allowed-origins:http://localhost:*}'.split(',')}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOrigins.stream().map(String::trim).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
