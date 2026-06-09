package com.pfc.auth;

import com.pfc.auth.dto.AuthResponse;
import com.pfc.auth.dto.LoginRequest;
import com.pfc.auth.dto.RegisterRequest;
import com.pfc.shared.exception.BusinessException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra cadastro e login: persiste credenciais (com hash) e emite o JWT
 * que autentica as requisições subsequentes.
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository repository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /**
     * Cadastra um novo usuário com a senha já em hash (BCrypt — nunca em texto
     * puro) e devolve um token de acesso imediatamente, autenticando o usuário
     * no mesmo passo do cadastro. Isso evita uma segunda chamada de login logo
     * após o registro — troca de conveniência por uma superfície ligeiramente
     * maior (token emitido sem passo de login explícito), aceitável para uma
     * API própria sem fluxo de verificação de e-mail nesta fase.
     *
     * @throws BusinessException se já existir usuário com o e-mail informado (e-mail é único)
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (repository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email '" + request.getEmail() + "' is already registered");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        repository.save(user);

        return issueToken(user);
    }

    /**
     * Autentica e-mail/senha via {@link AuthenticationManager} (que delega ao
     * {@link CustomUserDetailsService} e ao {@link PasswordEncoder}) e emite um
     * novo token em caso de sucesso. Credenciais inválidas resultam em
     * {@code BadCredentialsException}, convertida em 401 pelo
     * {@code AuthenticationEntryPoint} — nunca em erro 500.
     */
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = repository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user '" + request.getEmail() + "' not found in repository"));

        return issueToken(user);
    }

    private AuthResponse issueToken(User user) {
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, jwtService.extractExpiration(token));
    }
}
