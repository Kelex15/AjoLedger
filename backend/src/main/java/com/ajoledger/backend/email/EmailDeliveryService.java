package com.ajoledger.backend.email;

import com.ajoledger.backend.email.transport.EmailDeliveryException;
import com.ajoledger.backend.email.transport.EmailTransport;
import com.ajoledger.backend.system.AppProperties;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class EmailDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryService.class);

    private static final int BATCH_SIZE = 50;
    private static final Duration BASE_RETRY_DELAY = Duration.ofSeconds(30);

    private final EmailOutboxDao emailOutboxDao;
    private final EmailTransport emailTransport;
    private final AppProperties appProperties;

    /** Deliberately not transactional: each row's outcome commits on its own, so no transaction
     * stays open across a provider call. */
    public int deliverDue() {
        int sent = 0;
        for (OutboxEmail email : emailOutboxDao.findDue(Instant.now(), BATCH_SIZE)) {
            if (deliver(email)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean deliver(OutboxEmail email) {
        if (email.expiresAt() != null && email.expiresAt().isBefore(Instant.now())) {
            emailOutboxDao.markFailed(email.id(), email.attempts(), "Expired before it could be delivered");
            log.error("Email {} expired after {} attempts without being delivered", email.id(), email.attempts());
            return false;
        }

        int attempts = email.attempts() + 1;
        try {
            emailTransport.send(email);
            emailOutboxDao.markSent(email.id(), attempts);
            return true;
        } catch (RuntimeException e) {
            // Anything unexpected counts as retryable, so it still runs out of attempts instead of looping forever.
            boolean retryable = !(e instanceof EmailDeliveryException delivery) || delivery.isRetryable();
            if (retryable && attempts < appProperties.email().maxAttempts()) {
                Instant nextAttemptAt = Instant.now().plus(BASE_RETRY_DELAY.multipliedBy(1L << (attempts - 1)));
                emailOutboxDao.scheduleRetry(email.id(), attempts, nextAttemptAt, e.getMessage());
                log.warn("Email {} attempt {} failed, retrying at {}: {}", email.id(), attempts, nextAttemptAt, e.getMessage());
            } else {
                emailOutboxDao.markFailed(email.id(), attempts, e.getMessage());
                log.error("Email {} failed permanently after {} attempts: {}", email.id(), attempts, e.getMessage());
            }
            return false;
        }
    }

    public int purgeFinishedBefore(Instant cutoff) {
        return emailOutboxDao.deleteFinishedBefore(cutoff);
    }
}
