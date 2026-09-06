package com.ajoledger.backend.transaction.dto;

import com.ajoledger.backend.transaction.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponseDTO(
    UUID id,
    BigDecimal amount,
    String description,
    String transactionType,
    Instant createdAt
) {

    public static TransactionResponseDTO fromTransaction(Transaction transaction) {
        return new TransactionResponseDTO(
                transaction.id(),
                transaction.amount(),
                transaction.description(),
                transaction.transactionType().name(),
                transaction.createdAt());
    }
}
