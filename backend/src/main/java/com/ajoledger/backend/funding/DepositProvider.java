package com.ajoledger.backend.funding;

import com.ajoledger.backend.ledger.model.LedgerTransaction;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface DepositProvider {

    FundingType fundingType();

    /** Returns the URL to send the payer to. The deposit lands later, once the processor confirms. */
    String createDeposit(UUID userId, BigDecimal amount, String idempotencyKey);

    /** Empty while the processor hasn't confirmed payment yet. Rejects a reference the caller
     * doesn't own, so it is safe to reach straight from an authenticated request. */
    Optional<LedgerTransaction> settle(UUID currentUserId, String reference);
}
