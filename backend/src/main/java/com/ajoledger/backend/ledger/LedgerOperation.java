package com.ajoledger.backend.ledger;

import java.time.Instant;
import java.util.UUID;

/** Groups the LedgerTransactions belonging to one business event. */
public record LedgerOperation(
    UUID id,
    String operationType,
    UUID referenceId,
    Instant createdAt
) {

}
