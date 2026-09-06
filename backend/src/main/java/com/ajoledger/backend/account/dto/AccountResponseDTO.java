package com.ajoledger.backend.account.dto;

import java.math.BigDecimal;

import com.ajoledger.backend.account.Currency;

import lombok.Builder;

@Builder
public record AccountResponseDTO(String accountNumber, String accountType, BigDecimal balance, Currency currency) {
}
