package com.pfc.budget;

import com.pfc.budget.dto.BudgetRequest;
import com.pfc.budget.dto.BudgetResponse;
import com.pfc.category.Category;
import com.pfc.category.CategoryRepository;
import com.pfc.shared.exception.BusinessException;
import com.pfc.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Gerencia orçamentos mensais por categoria.
 * Reforça a restrição de unicidade {@code (category, referenceMonth)}: tentativas de duplicata
 * lançam {@link com.pfc.shared.exception.BusinessException}.
 */
@Service
@Transactional(readOnly = true)
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;

    public BudgetService(BudgetRepository budgetRepository, CategoryRepository categoryRepository) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<BudgetResponse> findAll() {
        return budgetRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public BudgetResponse findById(UUID id) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Budget", id));
        return toResponse(budget);
    }

    @Transactional
    public BudgetResponse create(BudgetRequest req) {
        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", req.getCategoryId()));

        budgetRepository.findByCategoryIdAndReferenceMonth(req.getCategoryId(), req.getReferenceMonth())
                .ifPresent(existing -> {
                    throw new BusinessException("Budget already exists for category and month");
                });

        Budget budget = new Budget();
        budget.setCategory(category);
        budget.setReferenceMonth(req.getReferenceMonth());
        budget.setLimitAmount(req.getLimitAmount());

        return toResponse(budgetRepository.save(budget));
    }

    /**
     * Atualiza um orçamento existente.
     * Verifica duplicata de {@code (category, referenceMonth)} excluindo o próprio registro
     * da checagem, para permitir atualizar apenas o {@code limitAmount} sem falsa colisão.
     */
    @Transactional
    public BudgetResponse update(UUID id, BudgetRequest req) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Budget", id));

        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", req.getCategoryId()));

        budgetRepository.findByCategoryIdAndReferenceMonth(req.getCategoryId(), req.getReferenceMonth())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessException("Budget already exists for category and month");
                });

        budget.setCategory(category);
        budget.setReferenceMonth(req.getReferenceMonth());
        budget.setLimitAmount(req.getLimitAmount());

        return toResponse(budgetRepository.save(budget));
    }

    @Transactional
    public void delete(UUID id) {
        if (!budgetRepository.existsById(id)) {
            throw new ResourceNotFoundException("Budget", id);
        }
        budgetRepository.deleteById(id);
    }

    private BudgetResponse toResponse(Budget b) {
        BudgetResponse response = new BudgetResponse();
        response.setId(b.getId());
        response.setCategoryId(b.getCategory().getId());
        response.setCategoryName(b.getCategory().getName());
        response.setCategoryType(b.getCategory().getType());
        response.setReferenceMonth(b.getReferenceMonth());
        response.setLimitAmount(b.getLimitAmount());
        return response;
    }
}
