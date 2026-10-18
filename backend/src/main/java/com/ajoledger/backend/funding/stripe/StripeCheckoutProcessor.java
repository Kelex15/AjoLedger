package com.ajoledger.backend.funding.stripe;

import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.funding.FundingService;
import com.ajoledger.backend.funding.FundingType;
import com.ajoledger.backend.ledger.model.LedgerTransaction;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Turns a paid Stripe checkout session into a ledger deposit. Shared by the webhook path and
 * the checkout-return verify/self-heal path; postDeposit's own idempotency key makes it safe for
 * both to race on the same session. */
@Component
@RequiredArgsConstructor
public class StripeCheckoutProcessor {

    private static final Logger log = LoggerFactory.getLogger(StripeCheckoutProcessor.class);

    private final FundingService fundingService;

    public Optional<LedgerTransaction> processIfPaid(PaidCheckoutSession session) {
        if (!"paid".equals(session.paymentStatus())) {
            log.info("Stripe checkout session {} has payment_status {}, not processing yet",
                    session.sessionId(), session.paymentStatus());
            return Optional.empty();
        }

        LedgerTransaction transaction = fundingService.postDeposit(session.userId(), UserAccountType.CHECKING,
                FundingType.STRIPE, session.amount(), session.idempotencyKey());
        return Optional.of(transaction);
    }
}
