package com.pfc.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.function.Function;

/**
 * Gera, lê e valida tokens JWT assinados com HMAC-SHA (HS256).
 * O segredo e o tempo de expiração vêm de configuração externa
 * ({@code jwt.secret} / {@code jwt.expiration-ms}) — nunca hardcoded —
 * para permitir rotação e valores distintos por ambiente.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.expiration-ms:3600000}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    /**
     * Emite um token contendo o e-mail do usuário como subject e a expiração
     * configurada ({@code jwt.expiration-ms}, padrão 1h). Tokens são stateless:
     * não há revogação/refresh nesta fase — expiram naturalmente.
     */
    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiration = now.plus(expirationMs, ChronoUnit.MILLIS);

        return Jwts.builder()
                .subject(user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(signingKey)
                .compact();
    }

    public Instant extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration).toInstant();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Token é válido quando a assinatura confere, o subject corresponde ao
     * usuário carregado e ele ainda não expirou.
     *
     * <p>jjwt já rejeita tokens expirados durante o parsing (lança
     * {@link ExpiredJwtException}); essa exceção é capturada aqui e tratada
     * como "inválido" para que o método tenha um contrato simples de boolean
     * — quem chama não precisa distinguir "expirado" de "assinatura ruim".
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String email = extractEmail(token);
            return email.equals(userDetails.getUsername());
        } catch (ExpiredJwtException ex) {
            return false;
        }
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }
}
