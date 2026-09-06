package com.ajoledger.backend.transaction.idempotency;

import java.time.Instant;
import java.util.UUID;

public record IdempotencyRecord(
    UUID id,
    String idempotencyKey,
    String requestType,
    String requestHash,
    IdempotencyStatus status,
    UUID transactionId,
    String failureReason,
    Instant createdAt,
    Instant completedAt
) {

}
