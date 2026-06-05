package com.pfc.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    Optional<Budget> findByCategoryIdAndReferenceMonth(UUID categoryId, String referenceMonth);

    List<Budget> findByReferenceMonth(String referenceMonth);
}
