package com.pfc.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BudgetComparisonResponse(
        UUID categoryId,
        String categoryName,
        String referenceMonth,
        BigDecimal budgeted,
        BigDecimal spent,
        BigDecimal remaining
) {}
