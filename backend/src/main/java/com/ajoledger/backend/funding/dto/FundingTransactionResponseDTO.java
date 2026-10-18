package com.ajoledger.backend.funding.dto;

import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.ledger.model.LedgerTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FundingTransactionResponseDTO(
    UUID id,
    BigDecimal amount,
    Currency currency,
    String description,
    String transactionType,
    Instant createdAt
) {

    public static FundingTransactionResponseDTO fromTransaction(LedgerTransaction transaction) {
        return new FundingTransactionResponseDTO(
                transaction.id(),
                transaction.amount(),
                transaction.currency(),
                transaction.description(),
                transaction.transactionType().name(),
                transaction.createdAt());
    }
}
