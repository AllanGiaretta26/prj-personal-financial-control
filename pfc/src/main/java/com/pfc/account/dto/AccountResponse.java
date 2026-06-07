package com.pfc.account.dto;

import com.pfc.account.AccountType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class AccountResponse {

    private UUID id;
    private String name;
    private AccountType type;
    private BigDecimal initialBalance;
    private LocalDateTime createdAt;
}
