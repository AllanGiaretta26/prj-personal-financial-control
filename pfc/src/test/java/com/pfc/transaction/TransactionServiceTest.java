package com.pfc.transaction;

import com.pfc.account.Account;
import com.pfc.account.AccountRepository;
import com.pfc.account.AccountType;
import com.pfc.auth.AuthenticatedUserProvider;
import com.pfc.auth.User;
import com.pfc.category.Category;
import com.pfc.category.CategoryRepository;
import com.pfc.category.CategoryType;
import com.pfc.shared.exception.ResourceNotFoundException;
import com.pfc.transaction.dto.TransactionRequest;
import com.pfc.transaction.dto.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AuthenticatedUserProvider authenticatedUserProvider;

    @InjectMocks
    private TransactionService service;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = buildUser(UUID.randomUUID(), "owner@example.com");
        when(authenticatedUserProvider.getCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void create_whenAccountNotFound_throwsResourceNotFoundException() {
        UUID accountId = UUID.randomUUID();
        TransactionRequest req = buildRequest("Test", BigDecimal.TEN, TransactionType.EXPENSE,
                accountId, UUID.randomUUID());

        when(accountRepository.findByIdAndOwner(accountId, currentUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account")
                .hasMessageContaining(accountId.toString());
    }

    @Test
    void create_whenCategoryNotFound_throwsResourceNotFoundException() {
        UUID accountId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        TransactionRequest req = buildRequest("Test", BigDecimal.TEN, TransactionType.EXPENSE,
                accountId, categoryId);

        Account account = buildAccount(accountId);
        when(accountRepository.findByIdAndOwner(accountId, currentUser)).thenReturn(Optional.of(account));
        when(categoryRepository.findByIdAndOwner(categoryId, currentUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category")
                .hasMessageContaining(categoryId.toString());
    }

    @Test
    void create_whenAccountBelongsToAnotherUser_throwsResourceNotFoundException() {
        UUID accountId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        TransactionRequest req = buildRequest("Test", BigDecimal.TEN, TransactionType.EXPENSE,
                accountId, categoryId);

        // findByIdAndOwner scoped to currentUser returns empty because the account belongs to someone else
        when(accountRepository.findByIdAndOwner(accountId, currentUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account")
                .hasMessageContaining(accountId.toString());

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void create_savesAndReturnsResponse() {
        UUID accountId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        TransactionRequest req = buildRequest("Salary", new BigDecimal("3000.00"), TransactionType.INCOME,
                accountId, categoryId);

        Account account = buildAccount(accountId);
        Category category = buildCategory(categoryId);

        Transaction saved = buildTransaction(UUID.randomUUID(), req, account, category);

        when(accountRepository.findByIdAndOwner(accountId, currentUser)).thenReturn(Optional.of(account));
        when(categoryRepository.findByIdAndOwner(categoryId, currentUser)).thenReturn(Optional.of(category));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

        TransactionResponse response = service.create(req);

        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(response.getDescription()).isEqualTo("Salary");
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("3000.00"));
        assertThat(response.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(response.getAccountId()).isEqualTo(accountId);
        assertThat(response.getCategoryId()).isEqualTo(categoryId);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void findById_whenNotFound_throwsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(transactionRepository.findByIdAndOwner(id, currentUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Transaction")
                .hasMessageContaining(id.toString());
    }

    private User buildUser(UUID id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPasswordHash("hash");
        return user;
    }

    private TransactionRequest buildRequest(String description, BigDecimal amount,
                                            TransactionType type, UUID accountId, UUID categoryId) {
        TransactionRequest req = new TransactionRequest();
        req.setDescription(description);
        req.setAmount(amount);
        req.setOccurredOn(LocalDate.now());
        req.setType(type);
        req.setAccountId(accountId);
        req.setCategoryId(categoryId);
        return req;
    }

    private Account buildAccount(UUID id) {
        Account account = new Account();
        setField(account, "id", id);
        account.setName("Main Account");
        account.setType(AccountType.CHECKING);
        account.setInitialBalance(BigDecimal.ZERO);
        account.setOwner(currentUser);
        return account;
    }

    private Category buildCategory(UUID id) {
        Category category = new Category();
        category.setId(id);
        category.setName("Food");
        category.setType(CategoryType.EXPENSE);
        category.setOwner(currentUser);
        return category;
    }

    private Transaction buildTransaction(UUID id, TransactionRequest req, Account account, Category category) {
        Transaction t = new Transaction();
        setField(t, "id", id);
        t.setDescription(req.getDescription());
        t.setAmount(req.getAmount());
        t.setOccurredOn(req.getOccurredOn());
        t.setType(req.getType());
        t.setAccount(account);
        t.setCategory(category);
        t.setOwner(currentUser);
        setField(t, "createdAt", LocalDateTime.now());
        return t;
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
