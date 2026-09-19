package com.ajoledger.backend.funding;

import com.ajoledger.backend.funding.dto.FundDepositRequestDTO;
import com.ajoledger.backend.funding.dto.FundWithdrawalRequestDTO;
import com.ajoledger.backend.funding.dto.FundingTransactionResponseDTO;
import com.ajoledger.backend.ledger.LedgerTransaction;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Manually funds the calling user's own account. A stand-in for real Stripe/PayPal SDK
 * integration, which isn't wired up yet. Still goes through the real ledger write path
 * (FundingService.postDeposit), just without an actual payment processor behind it. */
@RestController
@RequestMapping("/api/funding")
@RequiredArgsConstructor
public class FundingController {

    private final FundingService fundingService;

    @PostMapping(path = "/deposit", version = "1")
    public ResponseEntity<FundingTransactionResponseDTO> deposit(@AuthenticationPrincipal UUID currentUserId,
                                                            @Valid @RequestBody FundDepositRequestDTO dto) {
        LedgerTransaction transaction = fundingService.postDeposit(
                currentUserId, dto.targetType(), dto.fundingType(), dto.amount(), dto.idempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(FundingTransactionResponseDTO.fromTransaction(transaction));
    }

    @PostMapping(path = "/withdraw", version = "1")
    public ResponseEntity<FundingTransactionResponseDTO> withdraw(@AuthenticationPrincipal UUID currentUserId,
                                                            @Valid @RequestBody FundWithdrawalRequestDTO dto) {
        LedgerTransaction transaction = fundingService.postWithdrawal(
                currentUserId, dto.sourceType(), dto.fundingType(), dto.amount(), dto.idempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(FundingTransactionResponseDTO.fromTransaction(transaction));
    }
}
