package com.ajoledger.backend.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerTransaction(
    UUID id,
    BigDecimal amount,
    String description,
    LedgerTransactionType transactionType,
    UUID operationId,
    Instant createdAt
) {

}
