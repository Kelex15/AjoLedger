package com.ajoledger.backend.circle;

import com.ajoledger.backend.account.AccountService;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.account.entity.UserAccount;
import com.ajoledger.backend.account.exception.AccountNotFoundException;
import com.ajoledger.backend.circle.dto.CircleCreateRequestDTO;
import com.ajoledger.backend.circle.dto.CircleInviteCreateRequestDTO;
import com.ajoledger.backend.circle.dto.CircleInviteResponseDTO;
import com.ajoledger.backend.circle.dto.CircleMemberResponseDTO;
import com.ajoledger.backend.circle.dto.CircleMembershipResponseDTO;
import com.ajoledger.backend.circle.dto.CircleResponseDTO;
import com.ajoledger.backend.circle.dto.LienAmountRequestDTO;
import com.ajoledger.backend.circle.dto.LienStatusResponseDTO;
import com.ajoledger.backend.ledger.LedgerService;
import com.ajoledger.backend.ledger.LedgerTransaction;
import com.ajoledger.backend.ledger.dto.LedgerTransactionResponseDTO;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/circles")
@RequiredArgsConstructor
public class CircleController {

    private final CircleService circleService;
    private final AccountService accountService;
    private final LedgerService ledgerService;

    @PostMapping(version = "1")
    public ResponseEntity<CircleResponseDTO> create(@AuthenticationPrincipal UUID currentUserId,
                                                      @Valid @RequestBody CircleCreateRequestDTO dto) {
        Circle circle = circleService.createCircle(currentUserId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(CircleResponseDTO.fromCircle(circle));
    }

    @PostMapping(path = "/{circleId}/invites", version = "1")
    public ResponseEntity<CircleInviteResponseDTO> invite(@AuthenticationPrincipal UUID currentUserId,
                                                            @PathVariable UUID circleId,
                                                            @Valid @RequestBody CircleInviteCreateRequestDTO dto) {
        CircleInvite invite = circleService.inviteToCircle(circleId, currentUserId, dto.username(), dto.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(CircleInviteResponseDTO.fromInvite(invite));
    }

    @PostMapping(path = "/invites/{code}/redeem", version = "1")
    public ResponseEntity<CircleResponseDTO> redeem(@AuthenticationPrincipal UUID currentUserId,
                                                      @PathVariable String code) {
        Circle circle = circleService.redeemInvite(code, currentUserId);
        return ResponseEntity.ok(CircleResponseDTO.fromCircle(circle));
    }

    @PostMapping(path = "/{circleId}/join", version = "1")
    public ResponseEntity<CircleMembershipResponseDTO> join(@AuthenticationPrincipal UUID currentUserId,
                                                              @PathVariable UUID circleId) {
        CircleMembership membership = circleService.joinCircle(currentUserId, circleId);
        return ResponseEntity.status(HttpStatus.CREATED).body(CircleMembershipResponseDTO.fromMembership(membership));
    }

    @PostMapping(path = "/{circleId}/cancel", version = "1")
    public ResponseEntity<CircleResponseDTO> cancel(@AuthenticationPrincipal UUID currentUserId,
                                                      @PathVariable UUID circleId) {
        Circle circle = circleService.cancelCircle(circleId, currentUserId);
        return ResponseEntity.ok(CircleResponseDTO.fromCircle(circle));
    }

    @GetMapping(path = "/{circleId}/members", version = "1")
    public ResponseEntity<List<CircleMemberResponseDTO>> members(@PathVariable UUID circleId) {
        List<CircleMemberResponseDTO> members = circleService.getMemberStandings(circleId).stream()
                .map(CircleMemberResponseDTO::of)
                .toList();
        return ResponseEntity.ok(members);
    }

    @PostMapping(path = "/{circleId}/members/{memberUserId}/remove", version = "1")
    public ResponseEntity<CircleMembershipResponseDTO> removeMember(@AuthenticationPrincipal UUID currentUserId,
                                                                       @PathVariable UUID circleId,
                                                                       @PathVariable UUID memberUserId) {
        CircleMembership membership = circleService.removeMember(circleId, currentUserId, memberUserId);
        return ResponseEntity.ok(CircleMembershipResponseDTO.fromMembership(membership));
    }

    @GetMapping(path = "/lien", version = "1")
    public ResponseEntity<LienStatusResponseDTO> lienStatus(@AuthenticationPrincipal UUID currentUserId) {
        UserAccount lien = accountService.getUserAccount(currentUserId, UserAccountType.LIEN)
                .orElseThrow(() -> new AccountNotFoundException("No LIEN account for user: " + currentUserId));
        BigDecimal floor = circleService.computeLienWithdrawalFloor(currentUserId);
        return ResponseEntity.ok(LienStatusResponseDTO.of(lien, floor));
    }

    @PostMapping(path = "/lien/hold", version = "1")
    public ResponseEntity<LedgerTransactionResponseDTO> lienHold(@AuthenticationPrincipal UUID currentUserId,
                                                                   @Valid @RequestBody LienAmountRequestDTO dto) {
        LedgerTransaction transaction = circleService.postLienHold(currentUserId, dto.amount(), dto.idempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(LedgerTransactionResponseDTO.fromTransaction(transaction, ledgerService.getLegs(transaction.id())));
    }

    @PostMapping(path = "/lien/withdraw", version = "1")
    public ResponseEntity<LedgerTransactionResponseDTO> lienWithdraw(@AuthenticationPrincipal UUID currentUserId,
                                                                       @Valid @RequestBody LienAmountRequestDTO dto) {
        LedgerTransaction transaction = circleService.postLienWithdrawal(currentUserId, dto.amount(), dto.idempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(LedgerTransactionResponseDTO.fromTransaction(transaction, ledgerService.getLegs(transaction.id())));
    }
}
