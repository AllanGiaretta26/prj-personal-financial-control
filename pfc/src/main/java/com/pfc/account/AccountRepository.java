package com.pfc.account;

import com.pfc.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** Lista todas as contas pertencentes ao usuário, para isolamento por dono. */
    List<Account> findAllByOwner(User owner);

    /** Busca uma conta por id, restrita ao dono — usada para 404 sem vazar existência entre usuários. */
    Optional<Account> findByIdAndOwner(UUID id, User owner);

    /** Verifica existência de uma conta por id, restrita ao dono — usada antes de excluir. */
    boolean existsByIdAndOwner(UUID id, User owner);
}
