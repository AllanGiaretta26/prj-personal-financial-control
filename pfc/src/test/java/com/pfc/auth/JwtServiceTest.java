package com.pfc.auth;

import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-needs-32-chars-min";

    private final JwtService jwtService = new JwtService(SECRET, 3_600_000L);

    @Test
    void generateToken_thenExtractEmail_returnsOriginalEmail() {
        User user = buildUser("alice@example.com");

        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractEmail(token)).isEqualTo("alice@example.com");
    }

    @Test
    void generateToken_setsExpirationInTheFuture() {
        User user = buildUser("alice@example.com");
        Instant beforeGeneration = Instant.now();

        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractExpiration(token)).isAfter(beforeGeneration);
    }

    @Test
    void isTokenValid_whenEmailMatchesAndNotExpired_returnsTrue() {
        User user = buildUser("alice@example.com");
        String token = jwtService.generateToken(user);
        UserDetails userDetails = buildUserDetails("alice@example.com");

        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void isTokenValid_whenUsernameDiffersFromTokenSubject_returnsFalse() {
        User user = buildUser("alice@example.com");
        String token = jwtService.generateToken(user);
        UserDetails otherUser = buildUserDetails("bob@example.com");

        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    void isTokenValid_whenTokenAlreadyExpired_returnsFalse() {
        JwtService shortLivedJwtService = new JwtService(SECRET, -1_000L);
        User user = buildUser("alice@example.com");
        String token = shortLivedJwtService.generateToken(user);
        UserDetails userDetails = buildUserDetails("alice@example.com");

        assertThat(shortLivedJwtService.isTokenValid(token, userDetails)).isFalse();
    }

    @Test
    void extractEmail_whenTokenSignatureIsTampered_throwsException() {
        User user = buildUser("alice@example.com");
        String token = jwtService.generateToken(user);
        String tamperedToken = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.extractEmail(tamperedToken))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void extractEmail_whenTokenSignedWithDifferentSecret_throwsException() {
        JwtService otherJwtService = new JwtService("a-completely-different-secret-key-32-chars", 3_600_000L);
        User user = buildUser("alice@example.com");
        String token = otherJwtService.generateToken(user);

        assertThatThrownBy(() -> jwtService.extractEmail(token))
                .isInstanceOf(SignatureException.class);
    }

    private User buildUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("irrelevant-hash");
        return user;
    }

    private UserDetails buildUserDetails(String email) {
        return new org.springframework.security.core.userdetails.User(email, "irrelevant-hash", Collections.emptyList());
    }
}
