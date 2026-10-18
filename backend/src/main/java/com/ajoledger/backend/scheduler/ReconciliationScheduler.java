package com.ajoledger.backend.scheduler;

import com.ajoledger.backend.funding.paypal.PayPalReconciliationService;
import com.ajoledger.backend.funding.stripe.ReconciliationService;
import com.ajoledger.backend.system.AppProperties;
import com.ajoledger.backend.system.SchedulingConfig;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final ReconciliationService reconciliationService;
    private final PayPalReconciliationService payPalReconciliationService;
    private final AppProperties appProperties;

    @Scheduled(cron = "${ajoledger.scheduler.reconciliation-cron}", scheduler = SchedulingConfig.RECONCILIATION_TASK_SCHEDULER)
    public void runReconciliation() {
        Instant since = Instant.now().minus(Duration.ofHours(appProperties.scheduler().reconciliationLookbackHours()));
        try {
            reconciliationService.reconcile(since);
        } catch (Exception e) {
            log.error("Stripe reconciliation run failed", e);
        }
        try {
            payPalReconciliationService.reconcile(since);
        } catch (Exception e) {
            log.error("PayPal reconciliation run failed", e);
        }
    }
}
