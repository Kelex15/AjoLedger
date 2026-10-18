package com.ajoledger.backend.funding;

import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.funding.dto.FundDepositRequestDTO;
import com.ajoledger.backend.funding.dto.FundWithdrawalRequestDTO;
import com.ajoledger.backend.funding.dto.FundingDepositResponseDTO;
import com.ajoledger.backend.funding.dto.FundingTransactionResponseDTO;
import com.ajoledger.backend.ledger.model.LedgerTransaction;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Every deposit goes through a processor and returns a pending redirect, with the funds landing
 * later once that processor confirms. Withdrawals still post directly to the ledger. */
@RestController
@RequestMapping("/api/funding")
public class FundingController {

    private final FundingService fundingService;
    private final Map<FundingType, DepositProvider> depositProviders;

    public FundingController(FundingService fundingService, List<DepositProvider> depositProviders) {
        this.fundingService = fundingService;
        this.depositProviders = depositProviders.stream()
                .collect(Collectors.toMap(DepositProvider::fundingType, Function.identity()));
    }

    @PostMapping(path = "/deposit", version = "1")
    public ResponseEntity<FundingDepositResponseDTO> deposit(@AuthenticationPrincipal UUID currentUserId,
                                                            @Valid @RequestBody FundDepositRequestDTO dto) {
        String redirectUrl = provider(dto.fundingType())
                .createDeposit(currentUserId, dto.amount(), dto.idempotencyKey());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(FundingDepositResponseDTO.pendingRedirect(redirectUrl));
    }

    @PostMapping(path = "/withdraw", version = "1")
    public ResponseEntity<FundingTransactionResponseDTO> withdraw(@AuthenticationPrincipal UUID currentUserId,
                                                            @Valid @RequestBody FundWithdrawalRequestDTO dto) {
        LedgerTransaction transaction = fundingService.postWithdrawal(
                currentUserId, UserAccountType.CHECKING, dto.fundingType(), dto.amount(), dto.idempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(FundingTransactionResponseDTO.fromTransaction(transaction));
    }

    /** Called on return from Stripe checkout, so a deposit isn't left waiting on the webhook. */
    @PostMapping(path = "/stripe/checkout-sessions/{sessionId}/verify", version = "1")
    public ResponseEntity<FundingTransactionResponseDTO> verifyStripeCheckoutSession(
            @AuthenticationPrincipal UUID currentUserId, @PathVariable String sessionId) {
        return settle(FundingType.STRIPE, currentUserId, sessionId);
    }

    /** Called on return from PayPal approval, so a deposit isn't left waiting on the webhook. */
    @PostMapping(path = "/paypal/orders/{orderId}/settle", version = "1")
    public ResponseEntity<FundingTransactionResponseDTO> settlePayPalOrder(
            @AuthenticationPrincipal UUID currentUserId, @PathVariable String orderId) {
        return settle(FundingType.PAYPAL, currentUserId, orderId);
    }

    private ResponseEntity<FundingTransactionResponseDTO> settle(FundingType fundingType, UUID currentUserId, String reference) {
        Optional<LedgerTransaction> transaction = provider(fundingType).settle(currentUserId, reference);
        return transaction
                .map(t -> ResponseEntity.ok(FundingTransactionResponseDTO.fromTransaction(t)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }

    private DepositProvider provider(FundingType fundingType) {
        return depositProviders.get(fundingType);
    }
}
