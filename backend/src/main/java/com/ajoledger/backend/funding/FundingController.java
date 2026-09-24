package com.ajoledger.backend.funding;

import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.funding.dto.FundDepositRequestDTO;
import com.ajoledger.backend.funding.dto.FundWithdrawalRequestDTO;
import com.ajoledger.backend.funding.dto.FundingDepositResponseDTO;
import com.ajoledger.backend.funding.dto.FundingTransactionResponseDTO;
import com.ajoledger.backend.funding.stripe.StripeCheckoutService;
import com.ajoledger.backend.ledger.LedgerTransaction;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/** Deposit behavior depends on funding type: a processor-backed type returns a pending redirect
 * and the deposit lands later via webhook confirmation; anything without a processor behind it
 * still credits the account directly and synchronously. Withdrawals are always the latter. */
@RestController
@RequestMapping("/api/funding")
@RequiredArgsConstructor
public class FundingController {

    private final FundingService fundingService;
    private final StripeCheckoutService stripeCheckoutService;

    @PostMapping(path = "/deposit", version = "1")
    public ResponseEntity<FundingDepositResponseDTO> deposit(@AuthenticationPrincipal UUID currentUserId,
                                                            @Valid @RequestBody FundDepositRequestDTO dto) {
        if (dto.fundingType() == FundingType.STRIPE) {
            String checkoutUrl = stripeCheckoutService.createDepositCheckoutSession(currentUserId, dto.amount(), dto.idempotencyKey());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(FundingDepositResponseDTO.pendingRedirect(checkoutUrl));
        }
        LedgerTransaction transaction = fundingService.postDeposit(
                currentUserId, UserAccountType.CHECKING, dto.fundingType(), dto.amount(), dto.idempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(FundingDepositResponseDTO.completed(FundingTransactionResponseDTO.fromTransaction(transaction)));
    }

    @PostMapping(path = "/withdraw", version = "1")
    public ResponseEntity<FundingTransactionResponseDTO> withdraw(@AuthenticationPrincipal UUID currentUserId,
                                                            @Valid @RequestBody FundWithdrawalRequestDTO dto) {
        LedgerTransaction transaction = fundingService.postWithdrawal(
                currentUserId, UserAccountType.CHECKING, dto.fundingType(), dto.amount(), dto.idempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(FundingTransactionResponseDTO.fromTransaction(transaction));
    }

    /** Called by the frontend right after returning from Stripe checkout, so a deposit isn't
     * stuck waiting on the webhook (or the hourly reconciliation job) if either is delayed or
     * the webhook never arrives at all. */
    @PostMapping(path = "/stripe/checkout-sessions/{sessionId}/verify", version = "1")
    public ResponseEntity<FundingTransactionResponseDTO> verifyStripeCheckoutSession(
            @AuthenticationPrincipal UUID currentUserId, @PathVariable String sessionId) {
        Optional<LedgerTransaction> transaction = stripeCheckoutService.verifyAndHeal(currentUserId, sessionId);
        return transaction
                .map(t -> ResponseEntity.ok(FundingTransactionResponseDTO.fromTransaction(t)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }
}
