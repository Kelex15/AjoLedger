package com.ajoledger.backend.transaction.idempotency;

public enum IdempotencyStatus {
    IN_PROGRESS,
    SUCCEEDED,
    FAILED
}
