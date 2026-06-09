package com.pfc.shared.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfc.auth.JwtService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Aplica limitação de taxa (token-bucket, via Bucket4j) a todas as rotas
 * {@code /api/v1/**}, antes de qualquer outro processamento — ver
 * SECURITY.md > "Limitação de taxa".
 *
 * <p><strong>Posição na cadeia</strong>: registrado em {@code SecurityConfig}
 * com {@code addFilterBefore(..., JwtAuthenticationFilter.class)}, ou seja,
 * roda dentro da {@code SecurityFilterChain} mas antes da autenticação JWT
 * acontecer. Isso é necessário porque (a) o filtro precisa proteger as rotas
 * públicas de auth — que nunca passam por autenticação — e (b) requisições que
 * estourarem o limite devem ser rejeitadas o quanto antes, sem gastar trabalho
 * com parsing de credenciais, autenticação ou acesso ao banco. Como consequência,
 * o {@link org.springframework.security.core.context.SecurityContextHolder}
 * ainda não está populado quando este filtro roda — por isso ele extrai e
 * valida o JWT diretamente via {@link JwtService}, de forma independente do
 * {@code JwtAuthenticationFilter}.
 *
 * <p><strong>Estratégia de chave</strong>:
 * <ul>
 *   <li>Rotas {@code /api/v1/auth/login} e {@code /api/v1/auth/register}
 *       (alvo clássico de força bruta): perfil <em>estrito</em>
 *       ({@code rate-limit.login.*}), sempre por IP — são públicas, não há
 *       identidade para usar como chave;</li>
 *   <li>Demais rotas {@code /api/v1/**}: perfil <em>generoso</em>
 *       ({@code rate-limit.default.*}), por usuário (extraído do token, se
 *       presente e válido) ou, na ausência de um token válido, por IP — uma
 *       requisição sem token em rota protegida acabará recebendo 401 do
 *       Spring Security de qualquer forma, mas ainda assim é contabilizada
 *       para conter enchentes de requisições não autenticadas.</li>
 * </ul>
 *
 * <p><strong>Extração de IP e {@code X-Forwarded-For}</strong>: o header
 * {@code X-Forwarded-For} só é confiável quando a aplicação está atrás de um
 * proxy reverso confiável que o sobrescreve a cada salto — é o caso em
 * produção (a plataforma de deploy termina TLS e repassa as requisições).
 * Confiar nesse header sem um proxy nessa posição permitiria que o próprio
 * cliente forjasse seu IP e contornasse o limite (um vetor de bypass). Como
 * não há, hoje, configuração de proxies confiáveis (ex.: lista de CIDRs) nesta
 * aplicação, o filtro usa exclusivamente {@link HttpServletRequest#getRemoteAddr()}.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String RETRY_AFTER_HEADER = "Retry-After";

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String API_PATH_PREFIX = "/api/v1/";

    private static final String IP_KEY_PREFIX = "ip:";
    private static final String USER_KEY_PREFIX = "user:";

    private final RateLimitBucketProvider bucketProvider;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitBucketProvider bucketProvider, JwtService jwtService, ObjectMapper objectMapper) {
        this.bucketProvider = bucketProvider;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!path.startsWith(API_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket;
        if (path.equals(LOGIN_PATH) || path.equals(REGISTER_PATH)) {
            bucket = bucketProvider.loginBucket(IP_KEY_PREFIX + clientIp(request));
        } else {
            bucket = bucketProvider.defaultBucket(resolveDefaultKey(request));
        }

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        rejectWithTooManyRequests(response, probe);
    }

    /**
     * Chave do perfil generoso: por usuário quando o token presente é válido;
     * por IP quando ausente, malformado, expirado ou com assinatura inválida —
     * nesse caso a rota protegida acabará respondendo 401, mas a requisição
     * ainda precisa ser contabilizada para que enchentes não autenticadas
     * também sejam contidas.
     */
    private String resolveDefaultKey(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            try {
                String email = jwtService.extractEmail(token);
                if (email != null) {
                    return USER_KEY_PREFIX + email;
                }
            } catch (JwtException | IllegalArgumentException ex) {
                // Token malformado, expirado ou com assinatura inválida: cai para a chave por IP.
            }
        }

        return IP_KEY_PREFIX + clientIp(request);
    }

    /**
     * Apenas {@link HttpServletRequest#getRemoteAddr()} — ver Javadoc da
     * classe sobre por que {@code X-Forwarded-For} não é confiado aqui.
     */
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private void rejectWithTooManyRequests(HttpServletResponse response, ConsumptionProbe probe) throws IOException {
        long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Try again later.");

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(RETRY_AFTER_HEADER, String.valueOf(retryAfterSeconds));
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
