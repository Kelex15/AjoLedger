package com.ajoledger.backend.email;

import java.time.Instant;
import java.util.UUID;

public record OutboxEmail(
    UUID id,
    String recipient,
    String subject,
    String body,
    int attempts,
    Instant expiresAt
) {
}
