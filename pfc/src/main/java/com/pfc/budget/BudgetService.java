package com.pfc.budget;

import com.pfc.auth.AuthenticatedUserProvider;
import com.pfc.auth.User;
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
 *
 * <p>Toda operação é restrita ao usuário autenticado: cada orçamento pertence a um único
 * dono ({@link User}), e leituras/atualizações/exclusões são sempre filtradas por {@code owner}
 * — acesso a orçamento de outro usuário resulta em 404, nunca 403.
 */
@Service
@Transactional(readOnly = true)
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public BudgetService(BudgetRepository budgetRepository,
                         CategoryRepository categoryRepository,
                         AuthenticatedUserProvider authenticatedUserProvider) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    public List<BudgetResponse> findAll() {
        User currentUser = authenticatedUserProvider.getCurrentUser();
        return budgetRepository.findAllByOwner(currentUser).stream()
                .map(this::toResponse)
                .toList();
    }

    public BudgetResponse findById(UUID id) {
        User currentUser = authenticatedUserProvider.getCurrentUser();
        Budget budget = budgetRepository.findByIdAndOwner(id, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Budget", id));
        return toResponse(budget);
    }

    @Transactional
    public BudgetResponse create(BudgetRequest req) {
        User currentUser = authenticatedUserProvider.getCurrentUser();

        Category category = findOwnedCategory(req.getCategoryId(), currentUser);

        budgetRepository.findByCategoryIdAndReferenceMonth(req.getCategoryId(), req.getReferenceMonth())
                .ifPresent(existing -> {
                    throw new BusinessException("Budget already exists for category and month");
                });

        Budget budget = new Budget();
        budget.setCategory(category);
        budget.setReferenceMonth(req.getReferenceMonth());
        budget.setLimitAmount(req.getLimitAmount());
        budget.setOwner(currentUser);

        return toResponse(budgetRepository.save(budget));
    }

    /**
     * Atualiza um orçamento existente.
     * Verifica duplicata de {@code (category, referenceMonth)} excluindo o próprio registro
     * da checagem, para permitir atualizar apenas o {@code limitAmount} sem falsa colisão.
     */
    @Transactional
    public BudgetResponse update(UUID id, BudgetRequest req) {
        User currentUser = authenticatedUserProvider.getCurrentUser();

        Budget budget = budgetRepository.findByIdAndOwner(id, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Budget", id));

        Category category = findOwnedCategory(req.getCategoryId(), currentUser);

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
        User currentUser = authenticatedUserProvider.getCurrentUser();
        if (!budgetRepository.existsByIdAndOwner(id, currentUser)) {
            throw new ResourceNotFoundException("Budget", id);
        }
        budgetRepository.deleteById(id);
    }

    /**
     * Resolve a categoria referenciada pelo orçamento restringindo a busca ao usuário atual,
     * impedindo que um orçamento seja criado/atualizado apontando para a categoria de outro
     * usuário (vazamento/corrupção de dados entre contas — estilo IDOR). Categoria inexistente
     * e categoria de outro usuário resultam na mesma {@link ResourceNotFoundException} (404):
     * nunca revelar que o recurso existe sob outra conta.
     */
    private Category findOwnedCategory(UUID categoryId, User currentUser) {
        return categoryRepository.findByIdAndOwner(categoryId, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }

    private BudgetResponse toResponse(Budget b) {
        return new BudgetResponse(
                b.getId(),
                b.getCategory().getId(),
                b.getCategory().getName(),
                b.getCategory().getType(),
                b.getReferenceMonth(),
                b.getLimitAmount()
        );
    }
}
