package com.ajoledger.backend.ledger.hashchain.dto;

import com.ajoledger.backend.ledger.hashchain.LedgerHashChainVerificationService.VerificationResult;

public record LedgerHashChainVerificationResponseDTO(boolean clean, long entriesChecked, Long brokenAtSeq, String reason) {

    public static LedgerHashChainVerificationResponseDTO from(VerificationResult result) {
        return switch (result) {
            case VerificationResult.Clean clean ->
                new LedgerHashChainVerificationResponseDTO(true, clean.entriesChecked(), null, null);
            case VerificationResult.Broken broken ->
                new LedgerHashChainVerificationResponseDTO(false, broken.entriesCheckedBeforeFailure(), broken.seq(), broken.reason());
        };
    }
}
