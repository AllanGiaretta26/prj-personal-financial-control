package com.pfc.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Busca o usuário pelo e-mail (login). Usado tanto na autenticação
     * (carregar credenciais) quanto na resolução do usuário autenticado atual.
     */
    Optional<User> findByEmail(String email);

    /**
     * Verifica unicidade de e-mail no cadastro sem carregar a entidade inteira.
     */
    boolean existsByEmail(String email);
}
