package com.pfc.budget;

import com.pfc.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    Optional<Budget> findByCategoryIdAndReferenceMonth(UUID categoryId, String referenceMonth);

    List<Budget> findByReferenceMonth(String referenceMonth);

    /** Mesma consulta de {@link #findByReferenceMonth}, restrita ao dono — usada nos relatórios para isolamento por usuário. */
    List<Budget> findByReferenceMonthAndOwner(String referenceMonth, User owner);

    /** Lista todos os orçamentos pertencentes ao usuário, para isolamento por dono. */
    List<Budget> findAllByOwner(User owner);

    /** Busca um orçamento por id, restrito ao dono — usada para 404 sem vazar existência entre usuários. */
    Optional<Budget> findByIdAndOwner(UUID id, User owner);

    /** Verifica existência de um orçamento por id, restrito ao dono — usada antes de excluir. */
    boolean existsByIdAndOwner(UUID id, User owner);
}
