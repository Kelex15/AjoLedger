package com.ajoledger.backend.ledger.hashchain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerHashChainSealingService {

    private final LedgerHashChainDao ledgerHashChainDao;
    private final TransactionTemplate transactionTemplate;

    /** Appends every currently-unsealed ledger_operation/transaction/ledger_entry row to the
     * hash chain in one pass. Safe to call repeatedly: an already-sealed row is skipped
     * (UNIQUE(source_table, source_id) on ledger_hash_chain_entry), and this assumes only one
     * caller ever runs it at a time, same as every other scheduler in this codebase. */
    public int sealPendingRows() {
        return transactionTemplate.execute(status -> {
            List<LedgerHashChainDao.PendingRow> pending = ledgerHashChainDao.findPendingRows();
            if (pending.isEmpty()) {
                return 0;
            }

            LedgerHashChainDao.Tip tip = ledgerHashChainDao.findCurrentTip();
            long nextSeq = tip.seq() + 1;
            String prevHash = tip.hash();

            List<LedgerHashChainEntry> entries = new ArrayList<>();
            for (LedgerHashChainDao.PendingRow row : pending) {
                String hash = LedgerHashChainHasher.compute(row.sourceTable(), nextSeq, prevHash, row.rowJson());
                entries.add(new LedgerHashChainEntry(UUID.randomUUID(), nextSeq, row.sourceTable(),
                        row.sourceId(), row.sourceSeq(), prevHash, hash, Instant.now()));
                prevHash = hash;
                nextSeq++;
            }

            ledgerHashChainDao.insertEntries(entries);
            return entries.size();
        });
    }
}
