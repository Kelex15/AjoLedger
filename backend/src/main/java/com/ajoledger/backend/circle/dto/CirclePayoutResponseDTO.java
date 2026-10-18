package com.ajoledger.backend.circle.dto;

import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.circle.CirclePayout;
import com.ajoledger.backend.ledger.PayoutSplit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The amounts are null while the payout is recorded but its ledger operation hasn't posted yet.
 * amount is what the recipient now owns, savingsAmount plus reserveAmount. */
public record CirclePayoutResponseDTO(
    UUID id,
    int roundNumber,
    UUID recipientUserId,
    String recipientUsername,
    BigDecimal amount,
    BigDecimal savingsAmount,
    BigDecimal reserveAmount,
    Currency currency,
    Instant paidAt
) {

    public static CirclePayoutResponseDTO of(CirclePayout payout, Currency currency, PayoutSplit split) {
        return new CirclePayoutResponseDTO(
                payout.getId(),
                payout.getRoundNumber(),
                payout.getRecipient().getId(),
                payout.getRecipient().getUsername(),
                split == null ? null : split.total(),
                split == null ? null : split.savings(),
                split == null ? null : split.reserve(),
                currency,
                payout.getPaidAt());
    }
}
