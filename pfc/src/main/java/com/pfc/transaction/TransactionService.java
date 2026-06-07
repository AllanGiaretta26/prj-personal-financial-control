package com.pfc.transaction;

import com.pfc.account.Account;
import com.pfc.account.AccountRepository;
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
 */
@Service
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<TransactionResponse> findAll() {
        return transactionRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public TransactionResponse findById(UUID id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));
        return toResponse(transaction);
    }

    @Transactional
    public TransactionResponse create(TransactionRequest req) {
        Account account = accountRepository.findById(req.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", req.getAccountId()));
        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", req.getCategoryId()));

        Transaction transaction = new Transaction();
        transaction.setDescription(req.getDescription());
        transaction.setAmount(req.getAmount());
        transaction.setOccurredOn(req.getOccurredOn());
        transaction.setType(req.getType());
        transaction.setAccount(account);
        transaction.setCategory(category);

        return toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionResponse update(UUID id, TransactionRequest req) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));

        Account account = accountRepository.findById(req.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", req.getAccountId()));
        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", req.getCategoryId()));

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
        if (!transactionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Transaction", id);
        }
        transactionRepository.deleteById(id);
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
