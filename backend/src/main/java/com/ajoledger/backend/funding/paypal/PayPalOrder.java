package com.ajoledger.backend.funding.paypal;

import com.ajoledger.backend.account.Currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayPalOrder(
    UUID id,
    String orderId,
    UUID userId,
    String idempotencyKey,
    BigDecimal amount,
    Currency currency,
    Instant createdAt
) {
}
