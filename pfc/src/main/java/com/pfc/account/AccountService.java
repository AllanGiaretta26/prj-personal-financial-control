package com.pfc.account;

import com.pfc.account.dto.AccountRequest;
import com.pfc.account.dto.AccountResponse;
import com.pfc.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Gerencia o ciclo de vida das contas financeiras (CRUD).
 * Leituras usam transação somente-leitura; escritas sobrescrevem com {@code @Transactional}.
 */
@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    public List<AccountResponse> findAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public AccountResponse findById(UUID id) {
        Account account = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));
        return toResponse(account);
    }

    @Transactional
    public AccountResponse create(AccountRequest request) {
        Account account = new Account();
        account.setName(request.getName());
        account.setType(request.getType());
        account.setInitialBalance(request.getInitialBalance());
        return toResponse(repository.save(account));
    }

    @Transactional
    public AccountResponse update(UUID id, AccountRequest request) {
        Account account = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));
        account.setName(request.getName());
        account.setType(request.getType());
        account.setInitialBalance(request.getInitialBalance());
        return toResponse(repository.save(account));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Account", id);
        }
        repository.deleteById(id);
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getInitialBalance(),
                account.getCreatedAt()
        );
    }
}
