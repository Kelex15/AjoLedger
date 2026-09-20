package com.ajoledger.backend.ledger.dto;

import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.ledger.LedgerEntry;
import com.ajoledger.backend.ledger.LedgerTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LedgerTransactionResponseDTO(
    UUID id,
    BigDecimal amount,
    Currency currency,
    String description,
    String transactionType,
    UUID operationId,
    UUID fromAccountId,
    UUID toAccountId,
    Instant createdAt
) {

    public static LedgerTransactionResponseDTO fromTransaction(LedgerTransaction transaction, List<LedgerEntry> legs) {
        UUID fromAccountId = legs.stream().filter(leg -> leg.debitAmount().signum() > 0)
                .map(LedgerEntry::accountId).findFirst().orElse(null);
        UUID toAccountId = legs.stream().filter(leg -> leg.creditAmount().signum() > 0)
                .map(LedgerEntry::accountId).findFirst().orElse(null);
        return new LedgerTransactionResponseDTO(
                transaction.id(),
                transaction.amount(),
                transaction.currency(),
                transaction.description(),
                transaction.transactionType().name(),
                transaction.operationId(),
                fromAccountId,
                toAccountId,
                transaction.createdAt());
    }
}
