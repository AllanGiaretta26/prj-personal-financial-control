package com.pfc.budget;

import com.pfc.auth.AuthenticatedUserProvider;
import com.pfc.auth.User;
import com.pfc.budget.dto.BudgetRequest;
import com.pfc.budget.dto.BudgetResponse;
import com.pfc.category.Category;
import com.pfc.category.CategoryRepository;
import com.pfc.category.CategoryType;
import com.pfc.shared.exception.BusinessException;
import com.pfc.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetServiceTest {

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AuthenticatedUserProvider authenticatedUserProvider;

    @InjectMocks
    private BudgetService service;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = buildUser(UUID.randomUUID(), "owner@example.com");
        when(authenticatedUserProvider.getCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void create_whenDuplicateMonthCategory_throwsBusinessException() {
        UUID categoryId = UUID.randomUUID();
        BudgetRequest req = buildRequest(categoryId, "2025-01", new BigDecimal("500.00"));

        Category category = buildCategory(categoryId);
        Budget existing = buildBudget(UUID.randomUUID(), category, "2025-01", new BigDecimal("300.00"));

        when(categoryRepository.findByIdAndOwner(categoryId, currentUser)).thenReturn(Optional.of(category));
        when(budgetRepository.findByCategoryIdAndReferenceMonth(categoryId, "2025-01"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Budget already exists for category and month");
    }

    @Test
    void create_whenCategoryBelongsToAnotherUser_throwsResourceNotFoundException() {
        UUID categoryId = UUID.randomUUID();
        BudgetRequest req = buildRequest(categoryId, "2025-01", new BigDecimal("500.00"));

        when(categoryRepository.findByIdAndOwner(categoryId, currentUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category")
                .hasMessageContaining(categoryId.toString());

        verify(budgetRepository, never()).save(any());
    }

    @Test
    void create_savesAndReturnsResponse() {
        UUID categoryId = UUID.randomUUID();
        BudgetRequest req = buildRequest(categoryId, "2025-02", new BigDecimal("1000.00"));

        Category category = buildCategory(categoryId);
        Budget saved = buildBudget(UUID.randomUUID(), category, "2025-02", new BigDecimal("1000.00"));

        when(categoryRepository.findByIdAndOwner(categoryId, currentUser)).thenReturn(Optional.of(category));
        when(budgetRepository.findByCategoryIdAndReferenceMonth(categoryId, "2025-02"))
                .thenReturn(Optional.empty());
        when(budgetRepository.save(any(Budget.class))).thenReturn(saved);

        BudgetResponse response = service.create(req);

        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(response.getCategoryId()).isEqualTo(categoryId);
        assertThat(response.getCategoryName()).isEqualTo("Food");
        assertThat(response.getReferenceMonth()).isEqualTo("2025-02");
        assertThat(response.getLimitAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        verify(budgetRepository).save(any(Budget.class));
    }

    @Test
    void findById_whenNotFound_throwsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(budgetRepository.findByIdAndOwner(id, currentUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Budget")
                .hasMessageContaining(id.toString());
    }

    private User buildUser(UUID id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPasswordHash("hash");
        return user;
    }

    private BudgetRequest buildRequest(UUID categoryId, String referenceMonth, BigDecimal limitAmount) {
        BudgetRequest req = new BudgetRequest();
        req.setCategoryId(categoryId);
        req.setReferenceMonth(referenceMonth);
        req.setLimitAmount(limitAmount);
        return req;
    }

    private Category buildCategory(UUID id) {
        Category category = new Category();
        category.setId(id);
        category.setName("Food");
        category.setType(CategoryType.EXPENSE);
        category.setOwner(currentUser);
        return category;
    }

    private Budget buildBudget(UUID id, Category category, String referenceMonth, BigDecimal limitAmount) {
        Budget budget = new Budget();
        setField(budget, "id", id);
        budget.setCategory(category);
        budget.setReferenceMonth(referenceMonth);
        budget.setLimitAmount(limitAmount);
        budget.setOwner(currentUser);
        return budget;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
