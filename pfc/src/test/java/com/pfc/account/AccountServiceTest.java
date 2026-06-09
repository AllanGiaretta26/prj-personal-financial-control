package com.pfc.account;

import com.pfc.account.dto.AccountRequest;
import com.pfc.account.dto.AccountResponse;
import com.pfc.auth.AuthenticatedUserProvider;
import com.pfc.auth.User;
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
class AccountServiceTest {

    @Mock
    private AccountRepository repository;

    @Mock
    private AuthenticatedUserProvider authenticatedUserProvider;

    @InjectMocks
    private AccountService service;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = buildUser(UUID.randomUUID(), "owner@example.com");
        when(authenticatedUserProvider.getCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void findById_whenNotFound_throwsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndOwner(id, currentUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account")
                .hasMessageContaining(id.toString());
    }

    @Test
    void create_savesAndReturnsResponse() {
        AccountRequest request = buildRequest("My Checking", AccountType.CHECKING, BigDecimal.TEN);

        Account saved = buildAccount(UUID.randomUUID(), "My Checking", AccountType.CHECKING, BigDecimal.TEN);
        when(repository.save(any(Account.class))).thenReturn(saved);

        AccountResponse response = service.create(request);

        assertThat(response.getId()).isEqualTo(saved.getId());
        assertThat(response.getName()).isEqualTo("My Checking");
        assertThat(response.getType()).isEqualTo(AccountType.CHECKING);
        assertThat(response.getInitialBalance()).isEqualByComparingTo(BigDecimal.TEN);
        verify(repository).save(any(Account.class));
    }

    @Test
    void delete_whenNotFound_throwsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(repository.existsByIdAndOwner(id, currentUser)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account")
                .hasMessageContaining(id.toString());

        verify(repository, never()).deleteById(any());
    }

    @Test
    void update_whenFound_returnsUpdatedResponse() {
        UUID id = UUID.randomUUID();
        Account existing = buildAccount(id, "Old Name", AccountType.SAVINGS, BigDecimal.ZERO);
        AccountRequest request = buildRequest("New Name", AccountType.CHECKING, BigDecimal.valueOf(500));

        Account updated = buildAccount(id, "New Name", AccountType.CHECKING, BigDecimal.valueOf(500));
        when(repository.findByIdAndOwner(id, currentUser)).thenReturn(Optional.of(existing));
        when(repository.save(any(Account.class))).thenReturn(updated);

        AccountResponse response = service.update(id, request);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getType()).isEqualTo(AccountType.CHECKING);
        assertThat(response.getInitialBalance()).isEqualByComparingTo(BigDecimal.valueOf(500));
        verify(repository).save(existing);
    }

    @Test
    void update_whenNotOwnedByCurrentUser_throwsResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        AccountRequest request = buildRequest("New Name", AccountType.CHECKING, BigDecimal.valueOf(500));

        when(repository.findByIdAndOwner(id, currentUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account")
                .hasMessageContaining(id.toString());

        verify(repository, never()).save(any());
    }

    private User buildUser(UUID id, String email) {
        User user = new User();
        setField(user, "id", id);
        user.setEmail(email);
        user.setPasswordHash("hash");
        return user;
    }

    private AccountRequest buildRequest(String name, AccountType type, BigDecimal initialBalance) {
        AccountRequest req = new AccountRequest();
        req.setName(name);
        req.setType(type);
        req.setInitialBalance(initialBalance);
        return req;
    }

    private Account buildAccount(UUID id, String name, AccountType type, BigDecimal initialBalance) {
        Account account = new Account();
        setField(account, "id", id);
        account.setName(name);
        account.setType(type);
        account.setInitialBalance(initialBalance);
        setField(account, "createdAt", LocalDateTime.now());
        return account;
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
