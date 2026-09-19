package com.ajoledger.backend.account.dto;

import java.math.BigDecimal;

import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.account.entity.UserAccount;

import lombok.Builder;

@Builder
public record AccountResponseDTO(String accountNumber, String accountType, BigDecimal balance, Currency currency) {

    public static AccountResponseDTO fromAccount(UserAccount account) {
        return new AccountResponseDTO(
                account.getAccountNumber(),
                account.getUserAccountType().name(),
                account.getBalance(),
                account.getCurrency());
    }
}
