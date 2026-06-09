package com.pfc.transaction;

import com.pfc.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** Retorna todas as transações de uma conta, sem filtro de período. */
    List<Transaction> findByAccountId(UUID accountId);

    /**
     * Retorna transações de uma categoria em um intervalo fechado de datas.
     *
     * @param start primeiro dia do período (inclusivo)
     * @param end   último dia do período (inclusivo)
     */
    List<Transaction> findByCategoryIdAndOccurredOnBetween(UUID categoryId, LocalDate start, LocalDate end);

    /**
     * Mesma consulta de {@link #findByCategoryIdAndOccurredOnBetween}, restrita ao dono —
     * usada nos relatórios para isolamento por usuário (evita agregar lançamentos de outros usuários).
     */
    List<Transaction> findByCategoryIdAndOccurredOnBetweenAndOwner(UUID categoryId, LocalDate start, LocalDate end, User owner);

    /** Lista todas as transações pertencentes ao usuário, para isolamento por dono. */
    List<Transaction> findAllByOwner(User owner);

    /** Busca uma transação por id, restrita ao dono — usada para 404 sem vazar existência entre usuários. */
    Optional<Transaction> findByIdAndOwner(UUID id, User owner);

    /** Verifica existência de uma transação por id, restrita ao dono — usada antes de excluir. */
    boolean existsByIdAndOwner(UUID id, User owner);
}
