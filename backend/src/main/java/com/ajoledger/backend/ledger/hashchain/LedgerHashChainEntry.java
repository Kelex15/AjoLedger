package com.ajoledger.backend.ledger.hashchain;

import java.time.Instant;
import java.util.UUID;

public record LedgerHashChainEntry(
    UUID id,
    long seq,
    String sourceTable,
    UUID sourceId,
    long sourceSeq,
    String prevHash,
    String rowHash,
    Instant sealedAt
) {

}
