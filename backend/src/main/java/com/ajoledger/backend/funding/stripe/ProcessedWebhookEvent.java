package com.ajoledger.backend.funding.stripe;

import java.time.Instant;
import java.util.UUID;

public record ProcessedWebhookEvent(
    UUID id,
    String processor,
    String eventId,
    String rawPayload,
    Instant receivedAt,
    Instant processedAt,
    UUID ledgerTransactionId
) {
}
