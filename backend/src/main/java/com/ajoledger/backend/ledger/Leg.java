package com.ajoledger.backend.ledger;

import java.math.BigDecimal;
import java.util.UUID;

/** One account movement within a transaction. One row, one account, one direction (debit or credit). */
public record Leg(UUID accountId, BigDecimal debitAmount, BigDecimal creditAmount) {
}
