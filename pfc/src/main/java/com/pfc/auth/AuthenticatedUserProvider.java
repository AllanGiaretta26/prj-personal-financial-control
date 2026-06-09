package com.pfc.auth;

import com.pfc.shared.exception.ResourceNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolve a entidade {@link User} correspondente ao principal autenticado em
 * {@link SecurityContextHolder}.
 *
 * <p>Ponto único de acesso ao "usuário atual" — pensado para ser injetado nos
 * services das demais features (account, category, transaction, budget) na
 * próxima fase (refatoração de posse por usuário), evitando que cada um
 * reimplemente a leitura do {@code SecurityContext} e a busca no repositório.
 */
@Component
public class AuthenticatedUserProvider {

    private final UserRepository repository;

    public AuthenticatedUserProvider(UserRepository repository) {
        this.repository = repository;
    }

    /**
     * Retorna o {@link User} autenticado na requisição atual.
     *
     * @throws IllegalStateException    se não houver autenticação no contexto
     *                                  (não deveria ocorrer atrás do filtro JWT
     *                                  em endpoints protegidos — indica uso incorreto)
     * @throws ResourceNotFoundException se o e-mail do token não corresponder
     *                                  a nenhum usuário persistido (ex.: usuário
     *                                  removido após o token ser emitido)
     */
    @Transactional(readOnly = true)
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in the current security context");
        }

        String email = authentication.getName();
        return repository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }
}
