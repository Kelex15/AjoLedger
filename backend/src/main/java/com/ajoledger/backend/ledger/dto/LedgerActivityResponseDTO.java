package com.ajoledger.backend.ledger.dto;

import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.ledger.model.LedgerActivity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerActivityResponseDTO(
    UUID entryId,
    UUID transactionId,
    UserAccountType accountType,
    String accountNumber,
    BigDecimal amount,
    Currency currency,
    String description,
    String transactionType,
    String circleName,
    Integer roundNumber,
    Instant createdAt
) {

    public static LedgerActivityResponseDTO fromActivity(LedgerActivity activity) {
        return new LedgerActivityResponseDTO(
                activity.entryId(),
                activity.transactionId(),
                activity.accountType(),
                activity.accountNumber(),
                activity.amount(),
                activity.currency(),
                activity.description(),
                activity.transactionType().name(),
                activity.circleName(),
                activity.roundNumber(),
                activity.createdAt());
    }
}
