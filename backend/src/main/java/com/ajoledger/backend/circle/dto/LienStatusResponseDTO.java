package com.ajoledger.backend.circle.dto;

import com.ajoledger.backend.account.entity.UserAccount;

import java.math.BigDecimal;

public record LienStatusResponseDTO(
    String accountNumber,
    String currency,
    BigDecimal balance,
    BigDecimal withdrawalFloor,
    BigDecimal withdrawable
) {

    public static LienStatusResponseDTO of(UserAccount account, BigDecimal withdrawalFloor) {
        return new LienStatusResponseDTO(
                account.getAccountNumber(),
                account.getCurrency().name(),
                account.getBalance(),
                withdrawalFloor,
                account.getBalance().subtract(withdrawalFloor));
    }
}
