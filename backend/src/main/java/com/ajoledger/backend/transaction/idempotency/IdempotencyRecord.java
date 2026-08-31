package com.ajoledger.backend.transaction.idempotency;

import java.time.LocalDateTime;
import java.util.UUID;

public record IdempotencyRecord(
    UUID id,
    String idempotencyKey,
    String requestType,
    String requestHash,
    IdempotencyStatus status,
    UUID transactionId,
    String failureReason,
    LocalDateTime createdAt,
    LocalDateTime completedAt
) {

}
