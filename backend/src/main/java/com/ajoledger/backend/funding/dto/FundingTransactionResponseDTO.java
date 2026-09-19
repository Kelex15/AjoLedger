package com.ajoledger.backend.funding.dto;

import com.ajoledger.backend.ledger.LedgerTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Deliberately omits operationId and the internal from/to account ids that
 * LedgerTransactionResponseDTO exposes - for funding, those would leak the platform's
 * system clearing account ids to the caller. */
public record FundingTransactionResponseDTO(
    UUID id,
    BigDecimal amount,
    String description,
    String transactionType,
    Instant createdAt
) {

    public static FundingTransactionResponseDTO fromTransaction(LedgerTransaction transaction) {
        return new FundingTransactionResponseDTO(
                transaction.id(),
                transaction.amount(),
                transaction.description(),
                transaction.transactionType().name(),
                transaction.createdAt());
    }
}
