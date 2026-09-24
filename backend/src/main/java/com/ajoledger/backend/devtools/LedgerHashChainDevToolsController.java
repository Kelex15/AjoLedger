package com.ajoledger.backend.devtools;

import com.ajoledger.backend.ledger.hashchain.LedgerHashChainSealingService;
import com.ajoledger.backend.ledger.hashchain.LedgerHashChainVerificationService;
import com.ajoledger.backend.ledger.hashchain.dto.LedgerHashChainVerificationResponseDTO;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@RestController
@RequestMapping("/api/dev/ledger")
@RequiredArgsConstructor
public class LedgerHashChainDevToolsController {

    private final LedgerHashChainSealingService ledgerHashChainSealingService;
    private final LedgerHashChainVerificationService ledgerHashChainVerificationService;

    @PostMapping(path = "/seal-hash-chain", version = "1")
    public ResponseEntity<Integer> sealHashChain() {
        return ResponseEntity.ok(ledgerHashChainSealingService.sealPendingRows());
    }

    @GetMapping(path = "/verify-hash-chain", version = "1")
    public ResponseEntity<LedgerHashChainVerificationResponseDTO> verifyHashChain() {
        return ResponseEntity.ok(LedgerHashChainVerificationResponseDTO.from(ledgerHashChainVerificationService.verifyChain()));
    }
}
