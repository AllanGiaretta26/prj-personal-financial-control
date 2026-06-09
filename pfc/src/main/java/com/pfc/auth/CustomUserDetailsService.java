package com.pfc.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Carrega o {@link User} pelo e-mail (usado como username) e o adapta para o
 * {@link UserDetails} que o Spring Security espera. A entidade {@code User}
 * não implementa {@code UserDetails} diretamente para manter o modelo de
 * persistência desacoplado do contrato de autenticação do framework.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository repository;

    public CustomUserDetailsService(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = repository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User with email '" + email + "' not found"));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                Collections.emptyList());
    }
}
