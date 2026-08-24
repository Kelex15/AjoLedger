package com.ajoledger.backend.account.dto;

import java.math.BigDecimal;

import com.ajoledger.backend.account.Currency;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AccountResponseDTO {
    private String accountNumber;
    private String accountType;
    private BigDecimal balance;
    private Currency currency;
}
