package com.pfc.transaction.dto;

import com.pfc.transaction.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class TransactionRequest {

    @NotBlank
    @Size(max = 255)
    private String description;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotNull
    private LocalDate occurredOn;

    @NotNull
    private TransactionType type;

    @NotNull
    private UUID accountId;

    @NotNull
    private UUID categoryId;
}
