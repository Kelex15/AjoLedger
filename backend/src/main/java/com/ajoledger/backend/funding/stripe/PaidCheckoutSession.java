package com.ajoledger.backend.funding.stripe;

import com.ajoledger.backend.account.Currency;

import java.math.BigDecimal;
import java.util.UUID;

/** Normalized view of a Stripe checkout session, regardless of whether it came from a webhook's
 * raw JSON payload or a live API fetch. */
public record PaidCheckoutSession(
    String sessionId,
    String paymentStatus,
    UUID userId,
    String idempotencyKey,
    BigDecimal amount,
    Currency currency
) {
}
