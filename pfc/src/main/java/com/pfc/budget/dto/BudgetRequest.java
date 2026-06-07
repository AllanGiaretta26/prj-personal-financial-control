package com.pfc.budget.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class BudgetRequest {

    @NotNull
    private UUID categoryId;

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}")
    private String referenceMonth;

    @NotNull
    @Positive
    private BigDecimal limitAmount;
}
