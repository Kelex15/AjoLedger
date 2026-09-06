package com.ajoledger.backend.transaction;

import com.ajoledger.backend.transaction.dto.TransactionResponseDTO;
import com.ajoledger.backend.transaction.dto.TransferRequestDTO;

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

import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping(path = "/{id}", version = "1")
    public ResponseEntity<TransactionResponseDTO> getTransaction(@AuthenticationPrincipal UUID currentUserId,
                                                                   @PathVariable UUID id) {
        return transactionService.findTransactionForUser(id, currentUserId)
                .map(transaction -> ResponseEntity.ok(TransactionResponseDTO.fromTransaction(transaction)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(path = "/transfer", version = "1")
    public ResponseEntity<TransactionResponseDTO> transfer(@AuthenticationPrincipal UUID currentUserId,
                                                             @Valid @RequestBody TransferRequestDTO dto) {
        Transaction transaction = transactionService.postTransfer(
                currentUserId, dto.fromType(), dto.toUserId(), dto.toType(),
                dto.amount(), dto.idempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponseDTO.fromTransaction(transaction));
    }
}
