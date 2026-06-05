package com.pfc.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CategorySpendingResponse(
        UUID categoryId,
        String categoryName,
        BigDecimal totalSpent
) {}
