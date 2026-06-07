package com.pfc.transaction.dto;

import com.pfc.transaction.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class TransactionResponse {

    private UUID id;
    private String description;
    private BigDecimal amount;
    private LocalDate occurredOn;
    private TransactionType type;
    private UUID accountId;
    private String accountName;
    private UUID categoryId;
    private String categoryName;
    private LocalDateTime createdAt;
}
