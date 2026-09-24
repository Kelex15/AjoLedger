package com.ajoledger.backend.scheduler;

import com.ajoledger.backend.ledger.hashchain.LedgerHashChainSealingService;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LedgerHashChainSealingScheduler {

    private static final Logger log = LoggerFactory.getLogger(LedgerHashChainSealingScheduler.class);

    private final LedgerHashChainSealingService ledgerHashChainSealingService;

    @Scheduled(cron = "${ajoledger.scheduler.hash-chain-seal-cron}")
    public void sealPendingRows() {
        try {
            int sealed = ledgerHashChainSealingService.sealPendingRows();
            if (sealed > 0) {
                log.info("Sealed {} ledger rows into the hash chain", sealed);
            }
        } catch (Exception e) {
            log.error("Failed to seal pending ledger rows into the hash chain", e);
        }
    }
}
