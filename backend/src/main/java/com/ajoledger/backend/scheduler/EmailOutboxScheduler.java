package com.ajoledger.backend.scheduler;

import com.ajoledger.backend.email.EmailDeliveryService;
import com.ajoledger.backend.system.AppProperties;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class EmailOutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxScheduler.class);

    private final EmailDeliveryService emailDeliveryService;
    private final AppProperties appProperties;

    @Scheduled(cron = "${ajoledger.scheduler.email-outbox-cron}")
    public void deliverDueEmails() {
        try {
            int sent = emailDeliveryService.deliverDue();
            if (sent > 0) {
                log.info("Delivered {} queued emails", sent);
            }
        } catch (Exception e) {
            log.error("Email outbox delivery run failed", e);
        }
    }

    /** Sent and failed rows can carry a live verification link, so they don't stay around indefinitely. */
    @Scheduled(cron = "${ajoledger.scheduler.email-outbox-purge-cron}")
    public void purgeFinishedEmails() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(appProperties.email().retentionDays()));
        try {
            int purged = emailDeliveryService.purgeFinishedBefore(cutoff);
            if (purged > 0) {
                log.info("Purged {} finished emails from the outbox", purged);
            }
        } catch (Exception e) {
            log.error("Email outbox purge failed", e);
        }
    }
}
