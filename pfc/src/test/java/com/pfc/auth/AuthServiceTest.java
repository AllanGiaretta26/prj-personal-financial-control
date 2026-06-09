package com.pfc.auth;

import com.pfc.auth.dto.AuthResponse;
import com.pfc.auth.dto.LoginRequest;
import com.pfc.auth.dto.RegisterRequest;
import com.pfc.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService service;

    @Test
    void register_whenEmailNotTaken_savesUserWithHashedPasswordAndReturnsToken() {
        RegisterRequest request = buildRegisterRequest("alice@example.com", "supersecret");
        when(repository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("supersecret")).thenReturn("hashed-password");
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("generated-token");
        Instant expiration = Instant.now().plusSeconds(3600);
        when(jwtService.extractExpiration("generated-token")).thenReturn(expiration);

        AuthResponse response = service.register(request);

        assertThat(response.getToken()).isEqualTo("generated-token");
        assertThat(response.getExpiresAt()).isEqualTo(expiration);

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-password");
    }

    @Test
    void register_whenEmailAlreadyExists_throwsBusinessException() {
        RegisterRequest request = buildRegisterRequest("alice@example.com", "supersecret");
        when(repository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("alice@example.com");

        verify(repository, never()).save(any());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void login_withValidCredentials_authenticatesAndReturnsToken() {
        LoginRequest request = buildLoginRequest("alice@example.com", "supersecret");
        User user = buildUser(UUID.randomUUID(), "alice@example.com", "hashed-password");

        when(repository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("generated-token");
        Instant expiration = Instant.now().plusSeconds(3600);
        when(jwtService.extractExpiration("generated-token")).thenReturn(expiration);

        AuthResponse response = service.login(request);

        assertThat(response.getToken()).isEqualTo("generated-token");
        assertThat(response.getExpiresAt()).isEqualTo(expiration);
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void login_withBadCredentials_propagatesBadCredentialsException() {
        LoginRequest request = buildLoginRequest("alice@example.com", "wrong-password");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtService, never()).generateToken(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    private RegisterRequest buildRegisterRequest(String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private LoginRequest buildLoginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private User buildUser(UUID id, String email, String passwordHash) {
        User user = new User();
        setField(user, "id", id);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        return user;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
