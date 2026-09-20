package com.ajoledger.backend.ledger;

import com.ajoledger.backend.account.Currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerTransaction(
    UUID id,
    BigDecimal amount,
    Currency currency,
    String description,
    LedgerTransactionType transactionType,
    UUID operationId,
    Instant createdAt
) {

}
