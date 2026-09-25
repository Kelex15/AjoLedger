package com.ajoledger.backend.circle.dto;

import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.circle.CirclePayout;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Amount is null while the payout is recorded but its ledger operation hasn't posted yet. */
public record CirclePayoutResponseDTO(
    UUID id,
    int roundNumber,
    UUID recipientUserId,
    String recipientUsername,
    BigDecimal amount,
    Currency currency,
    Instant paidAt
) {

    public static CirclePayoutResponseDTO of(CirclePayout payout, Currency currency, BigDecimal amount) {
        return new CirclePayoutResponseDTO(
                payout.getId(),
                payout.getRoundNumber(),
                payout.getRecipient().getId(),
                payout.getRecipient().getUsername(),
                amount,
                currency,
                payout.getPaidAt());
    }
}
