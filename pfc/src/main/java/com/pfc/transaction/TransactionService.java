package com.pfc.transaction;

import com.pfc.account.Account;
import com.pfc.account.AccountRepository;
import com.pfc.auth.AuthenticatedUserProvider;
import com.pfc.auth.User;
import com.pfc.category.Category;
import com.pfc.category.CategoryRepository;
import com.pfc.shared.exception.ResourceNotFoundException;
import com.pfc.transaction.dto.TransactionRequest;
import com.pfc.transaction.dto.TransactionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Gerencia lançamentos de receita e despesa.
 * Na criação e atualização, valida a existência das referências de conta e categoria
 * antes de persistir, lançando {@link com.pfc.shared.exception.ResourceNotFoundException} se ausentes.
 *
 * <p>Toda operação é restrita ao usuário autenticado: cada transação pertence a um único
 * dono ({@link User}), e leituras/atualizações/exclusões são sempre filtradas por {@code owner}
 * — acesso a transação de outro usuário resulta em 404, nunca 403.
 */
@Service
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              CategoryRepository categoryRepository,
                              AuthenticatedUserProvider authenticatedUserProvider) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    public List<TransactionResponse> findAll() {
        User currentUser = authenticatedUserProvider.getCurrentUser();
        return transactionRepository.findAllByOwner(currentUser).stream()
                .map(this::toResponse)
                .toList();
    }

    public TransactionResponse findById(UUID id) {
        User currentUser = authenticatedUserProvider.getCurrentUser();
        Transaction transaction = transactionRepository.findByIdAndOwner(id, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));
        return toResponse(transaction);
    }

    @Transactional
    public TransactionResponse create(TransactionRequest req) {
        User currentUser = authenticatedUserProvider.getCurrentUser();

        Account account = findOwnedAccount(req.getAccountId(), currentUser);
        Category category = findOwnedCategory(req.getCategoryId(), currentUser);

        Transaction transaction = new Transaction();
        transaction.setDescription(req.getDescription());
        transaction.setAmount(req.getAmount());
        transaction.setOccurredOn(req.getOccurredOn());
        transaction.setType(req.getType());
        transaction.setAccount(account);
        transaction.setCategory(category);
        transaction.setOwner(currentUser);

        return toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionResponse update(UUID id, TransactionRequest req) {
        User currentUser = authenticatedUserProvider.getCurrentUser();

        Transaction transaction = transactionRepository.findByIdAndOwner(id, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));

        Account account = findOwnedAccount(req.getAccountId(), currentUser);
        Category category = findOwnedCategory(req.getCategoryId(), currentUser);

        transaction.setDescription(req.getDescription());
        transaction.setAmount(req.getAmount());
        transaction.setOccurredOn(req.getOccurredOn());
        transaction.setType(req.getType());
        transaction.setAccount(account);
        transaction.setCategory(category);

        return toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public void delete(UUID id) {
        User currentUser = authenticatedUserProvider.getCurrentUser();
        if (!transactionRepository.existsByIdAndOwner(id, currentUser)) {
            throw new ResourceNotFoundException("Transaction", id);
        }
        transactionRepository.deleteById(id);
    }

    /**
     * Resolve a conta referenciada pela transação restringindo a busca ao usuário atual,
     * impedindo que uma transação seja criada/atualizada apontando para a conta de outro
     * usuário (vazamento/corrupção de dados entre contas — estilo IDOR). Conta inexistente
     * e conta de outro usuário resultam na mesma {@link ResourceNotFoundException} (404):
     * nunca revelar que o recurso existe sob outra conta.
     */
    private Account findOwnedAccount(UUID accountId, User currentUser) {
        return accountRepository.findByIdAndOwner(accountId, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }

    /** Mesma validação de posse aplicada à categoria referenciada — ver {@link #findOwnedAccount}. */
    private Category findOwnedCategory(UUID categoryId, User currentUser) {
        return categoryRepository.findByIdAndOwner(categoryId, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
    }

    /** Desnormaliza nome da conta e da categoria no DTO para evitar lazy-load posterior. */
    private TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getDescription(),
                t.getAmount(),
                t.getOccurredOn(),
                t.getType(),
                t.getAccount().getId(),
                t.getAccount().getName(),
                t.getCategory().getId(),
                t.getCategory().getName(),
                t.getCreatedAt()
        );
    }
}
