package com.ajoledger.backend.transaction.dto;

import com.ajoledger.backend.transaction.Transaction;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class TransactionResponseDTO {
    private UUID id;
    private BigDecimal amount;
    private String description;
    private String transactionType;
    private Instant createdAt;

    public static TransactionResponseDTO fromTransaction(Transaction transaction) {
        return new TransactionResponseDTO(
                transaction.id(),
                transaction.amount(),
                transaction.description(),
                transaction.transactionType().name(),
                transaction.createdAt());
    }
}
