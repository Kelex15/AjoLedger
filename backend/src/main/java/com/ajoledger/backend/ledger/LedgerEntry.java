package com.ajoledger.backend.ledger;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record LedgerEntry(
    UUID id,
    UUID accountId,
    BigDecimal debitAmount,
    BigDecimal creditAmount,
    UUID transactionId,
    LocalDateTime createdAt
) {

}
