package com.pfc.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountBalanceResponse(
        UUID accountId,
        String accountName,
        BigDecimal initialBalance,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal currentBalance
) {}
