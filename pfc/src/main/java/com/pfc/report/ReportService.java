package com.pfc.report;

import com.pfc.account.Account;
import com.pfc.account.AccountRepository;
import com.pfc.auth.AuthenticatedUserProvider;
import com.pfc.auth.User;
import com.pfc.budget.Budget;
import com.pfc.budget.BudgetRepository;
import com.pfc.report.dto.AccountBalanceResponse;
import com.pfc.report.dto.BudgetComparisonResponse;
import com.pfc.report.dto.CategorySpendingResponse;
import com.pfc.transaction.Transaction;
import com.pfc.transaction.TransactionRepository;
import com.pfc.transaction.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Produz relatórios de agregação financeira sem estado próprio (sem entidade JPA).
 *
 * <p>Todas as agregações são feitas em memória via streams sobre os repositórios existentes.
 * Para volumes pessoais isso é adequado; se o volume crescer, migrar para queries JPQL/nativas.</p>
 *
 * <p>Como cada recurso financeiro agora pertence a um usuário, todas as agregações aqui
 * são restritas ao usuário autenticado ({@code owner}) — caso contrário, este serviço
 * vazaria dados financeiros agregados de outros usuários (ex.: somar transações de toda
 * a base em vez de apenas do usuário atual). Cada método resolve o usuário atual uma
 * única vez via {@link AuthenticatedUserProvider} e usa as variantes "ByOwner"/"AndOwner"
 * dos repositórios.</p>
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final AccountRepository accountRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public ReportService(TransactionRepository transactionRepository,
                         BudgetRepository budgetRepository,
                         AccountRepository accountRepository,
                         AuthenticatedUserProvider authenticatedUserProvider) {
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.accountRepository = accountRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    /**
     * Retorna o total gasto por categoria de despesa no mês informado, restrito às
     * transações do usuário autenticado.
     *
     * @param referenceMonth mês no formato {@code YYYY-MM} (ex.: {@code "2026-06"})
     */
    public List<CategorySpendingResponse> spendingByCategory(String referenceMonth) {
        User currentUser = authenticatedUserProvider.getCurrentUser();
        YearMonth yearMonth = YearMonth.parse(referenceMonth);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        Map<UUID, List<Transaction>> byCategory = transactionRepository.findAllByOwner(currentUser).stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .filter(t -> !t.getOccurredOn().isBefore(start) && !t.getOccurredOn().isAfter(end))
                .collect(Collectors.groupingBy(t -> t.getCategory().getId()));

        return byCategory.entrySet().stream()
                .map(entry -> {
                    List<Transaction> transactions = entry.getValue();
                    String categoryName = transactions.get(0).getCategory().getName();
                    BigDecimal totalSpent = transactions.stream()
                            .map(Transaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new CategorySpendingResponse(entry.getKey(), categoryName, totalSpent);
                })
                .toList();
    }

    /**
     * Compara o orçado com o realizado para cada categoria com orçamento cadastrado no mês,
     * restrito aos orçamentos e transações do usuário autenticado.
     * Categorias sem orçamento não aparecem no resultado.
     *
     * @param referenceMonth mês no formato {@code YYYY-MM}
     */
    public List<BudgetComparisonResponse> budgetComparison(String referenceMonth) {
        User currentUser = authenticatedUserProvider.getCurrentUser();
        YearMonth yearMonth = YearMonth.parse(referenceMonth);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        List<Budget> budgets = budgetRepository.findByReferenceMonthAndOwner(referenceMonth, currentUser);

        return budgets.stream()
                .map(budget -> {
                    BigDecimal spent = transactionRepository
                            .findByCategoryIdAndOccurredOnBetweenAndOwner(budget.getCategory().getId(), start, end, currentUser)
                            .stream()
                            .filter(t -> t.getType() == TransactionType.EXPENSE)
                            .map(Transaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal remaining = budget.getLimitAmount().subtract(spent);

                    return new BudgetComparisonResponse(
                            budget.getCategory().getId(),
                            budget.getCategory().getName(),
                            budget.getReferenceMonth(),
                            budget.getLimitAmount(),
                            spent,
                            remaining
                    );
                })
                .toList();
    }

    /**
     * Calcula o saldo atual de todas as contas do usuário autenticado.
     * Fórmula: {@code saldoAtual = saldoInicial + totalReceitas - totalDespesas}.
     */
    public List<AccountBalanceResponse> accountBalances() {
        User currentUser = authenticatedUserProvider.getCurrentUser();
        List<Account> accounts = accountRepository.findAllByOwner(currentUser);
        List<Transaction> allTransactions = transactionRepository.findAllByOwner(currentUser);

        return accounts.stream()
                .map(account -> {
                    List<Transaction> accountTransactions = allTransactions.stream()
                            .filter(t -> t.getAccount().getId().equals(account.getId()))
                            .toList();

                    BigDecimal totalIncome = accountTransactions.stream()
                            .filter(t -> t.getType() == TransactionType.INCOME)
                            .map(Transaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal totalExpense = accountTransactions.stream()
                            .filter(t -> t.getType() == TransactionType.EXPENSE)
                            .map(Transaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal currentBalance = account.getInitialBalance()
                            .add(totalIncome)
                            .subtract(totalExpense);

                    return new AccountBalanceResponse(
                            account.getId(),
                            account.getName(),
                            account.getInitialBalance(),
                            totalIncome,
                            totalExpense,
                            currentBalance
                    );
                })
                .toList();
    }
}
