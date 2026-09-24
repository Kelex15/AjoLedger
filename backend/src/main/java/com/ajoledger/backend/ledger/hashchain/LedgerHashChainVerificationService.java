package com.ajoledger.backend.ledger.hashchain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LedgerHashChainVerificationService {

    public sealed interface VerificationResult {
        record Clean(long entriesChecked) implements VerificationResult {
        }

        record Broken(long seq, String reason, long entriesCheckedBeforeFailure) implements VerificationResult {
        }
    }

    private final LedgerHashChainDao ledgerHashChainDao;

    /** Walks the whole chain in order, recomputing each entry's hash from its source row's
     * current content and checking continuity against its neighbor. A mismatch means the source
     * row's content changed after sealing, or a chain entry was itself tampered with or removed.
     * Both bypass the append-only triggers, which only stop ordinary SQL. */
    public VerificationResult verifyChain() {
        List<LedgerHashChainEntry> entries = ledgerHashChainDao.findAllOrderedBySeq();
        String expectedPrevHash = LedgerHashChainHasher.GENESIS_HASH;
        long checked = 0;

        for (LedgerHashChainEntry entry : entries) {
            if (!entry.prevHash().equals(expectedPrevHash)) {
                return new VerificationResult.Broken(entry.seq(),
                        "prev_hash does not match the previous entry's row_hash", checked);
            }

            Optional<String> currentRowJson = ledgerHashChainDao.currentRowJson(entry.sourceTable(), entry.sourceId());
            if (currentRowJson.isEmpty()) {
                return new VerificationResult.Broken(entry.seq(),
                        "source row " + entry.sourceId() + " in " + entry.sourceTable() + " no longer exists", checked);
            }

            String recomputedHash = LedgerHashChainHasher.compute(entry.sourceTable(), entry.seq(), entry.prevHash(), currentRowJson.get());
            if (!recomputedHash.equals(entry.rowHash())) {
                return new VerificationResult.Broken(entry.seq(),
                        "source row " + entry.sourceId() + " in " + entry.sourceTable() + " no longer matches its sealed hash", checked);
            }

            expectedPrevHash = entry.rowHash();
            checked++;
        }

        return new VerificationResult.Clean(checked);
    }
}
