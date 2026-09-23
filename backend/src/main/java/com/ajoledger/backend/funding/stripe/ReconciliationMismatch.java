package com.ajoledger.backend.funding.stripe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReconciliationMismatch(
    UUID id,
    String processor,
    String eventId,
    ReconciliationMismatchType mismatchType,
    BigDecimal expectedAmount,
    BigDecimal actualAmount,
    Instant detectedAt
) {
}
