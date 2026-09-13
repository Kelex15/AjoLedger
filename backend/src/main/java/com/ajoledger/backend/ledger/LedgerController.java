package com.ajoledger.backend.ledger;

import com.ajoledger.backend.ledger.dto.LedgerTransactionResponseDTO;
import com.ajoledger.backend.ledger.dto.TransferRequestDTO;

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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    @GetMapping(path = "/transactions/{id}", version = "1")
    public ResponseEntity<LedgerTransactionResponseDTO> getTransaction(@AuthenticationPrincipal UUID currentUserId,
                                                                         @PathVariable UUID id) {
        return ledgerService.findTransactionForUser(id, currentUserId)
                .map(transaction -> ResponseEntity.ok(
                        LedgerTransactionResponseDTO.fromTransaction(transaction, ledgerService.getLegs(transaction.id()))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(path = "/transfer", version = "1")
    public ResponseEntity<LedgerTransactionResponseDTO> transfer(@AuthenticationPrincipal UUID currentUserId,
                                                                   @Valid @RequestBody TransferRequestDTO dto) {
        LedgerTransaction transaction = ledgerService.postTransfer(
                currentUserId, dto.fromType(), dto.toUserId(), dto.toType(),
                dto.amount(), dto.idempotencyKey());
        List<LedgerEntry> legs = ledgerService.getLegs(transaction.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(LedgerTransactionResponseDTO.fromTransaction(transaction, legs));
    }
}
