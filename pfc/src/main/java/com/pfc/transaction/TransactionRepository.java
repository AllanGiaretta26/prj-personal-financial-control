package com.pfc.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
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
}
