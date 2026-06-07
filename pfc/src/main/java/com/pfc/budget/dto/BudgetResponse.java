package com.pfc.budget.dto;

import com.pfc.category.CategoryType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class BudgetResponse {

    private UUID id;
    private UUID categoryId;
    private String categoryName;
    private CategoryType categoryType;
    private String referenceMonth;
    private BigDecimal limitAmount;
}
