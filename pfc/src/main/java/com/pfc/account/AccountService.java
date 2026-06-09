package com.pfc.account;

import com.pfc.account.dto.AccountRequest;
import com.pfc.account.dto.AccountResponse;
import com.pfc.auth.AuthenticatedUserProvider;
import com.pfc.auth.User;
import com.pfc.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Gerencia o ciclo de vida das contas financeiras (CRUD).
 * Leituras usam transação somente-leitura; escritas sobrescrevem com {@code @Transactional}.
 *
 * <p>Toda operação é restrita ao usuário autenticado: cada conta pertence a um único
 * dono ({@link User}) e jamais é visível ou alterável por outro usuário — leituras,
 * atualizações e exclusões são sempre filtradas por {@code owner}, e tentativas de
 * acesso a contas de outro usuário resultam em {@link ResourceNotFoundException} (404),
 * nunca 403, para não revelar a existência do recurso a quem não é o dono.
 */
@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository repository;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public AccountService(AccountRepository repository, AuthenticatedUserProvider authenticatedUserProvider) {
        this.repository = repository;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    public List<AccountResponse> findAll() {
        User currentUser = authenticatedUserProvider.getCurrentUser();
        return repository.findAllByOwner(currentUser).stream()
                .map(this::toResponse)
                .toList();
    }

    public AccountResponse findById(UUID id) {
        User currentUser = authenticatedUserProvider.getCurrentUser();
        Account account = repository.findByIdAndOwner(id, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));
        return toResponse(account);
    }

    @Transactional
    public AccountResponse create(AccountRequest request) {
        User currentUser = authenticatedUserProvider.getCurrentUser();

        Account account = new Account();
        account.setName(request.getName());
        account.setType(request.getType());
        account.setInitialBalance(request.getInitialBalance());
        account.setOwner(currentUser);
        return toResponse(repository.save(account));
    }

    @Transactional
    public AccountResponse update(UUID id, AccountRequest request) {
        User currentUser = authenticatedUserProvider.getCurrentUser();
        Account account = repository.findByIdAndOwner(id, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));
        account.setName(request.getName());
        account.setType(request.getType());
        account.setInitialBalance(request.getInitialBalance());
        return toResponse(repository.save(account));
    }

    @Transactional
    public void delete(UUID id) {
        User currentUser = authenticatedUserProvider.getCurrentUser();
        if (!repository.existsByIdAndOwner(id, currentUser)) {
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
