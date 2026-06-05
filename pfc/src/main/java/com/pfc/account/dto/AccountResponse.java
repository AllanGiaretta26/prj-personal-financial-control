package com.pfc.account.dto;

import com.pfc.account.AccountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class AccountResponse {

    private UUID id;
    private String name;
    private AccountType type;
    private BigDecimal initialBalance;
    private LocalDateTime createdAt;

    public AccountResponse(UUID id, String name, AccountType type, BigDecimal initialBalance, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.initialBalance = initialBalance;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
