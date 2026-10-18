package com.ajoledger.backend.funding.stripe;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.stripe.StripeClient;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionListParams;

import com.ajoledger.backend.funding.processor.ProcessedWebhookEvent;
import com.ajoledger.backend.funding.processor.ProcessedWebhookEventDao;
import com.ajoledger.backend.funding.processor.ReconciliationMismatchDao;
import com.ajoledger.backend.funding.processor.ReconciliationMismatchType;
import com.ajoledger.backend.ledger.dao.LedgerTransactionDao;
import com.ajoledger.backend.ledger.model.LedgerTransaction;
import com.ajoledger.backend.ledger.idempotency.IdempotencyDao;
import com.ajoledger.backend.ledger.idempotency.IdempotencyRecord;
import com.ajoledger.backend.ledger.idempotency.IdempotencyStatus;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    private static final String PROCESSOR = "STRIPE";

    private final StripeClient stripeClient;
    private final IdempotencyDao idempotencyDao;
    private final LedgerTransactionDao ledgerTransactionDao;
    private final ProcessedWebhookEventDao processedWebhookEventDao;
    private final ReconciliationMismatchDao reconciliationMismatchDao;
    private final ObjectMapper objectMapper;

    public void reconcile(Instant since) {
        reconcileStripeAgainstLedger(since);
        reconcileLedgerAgainstStripe(since);
    }

    private void reconcileStripeAgainstLedger(Instant since) {
        SessionListParams params = SessionListParams.builder()
                .setStatus(SessionListParams.Status.COMPLETE)
                .setCreated(SessionListParams.Created.builder().setGte(since.getEpochSecond()).build())
                .setLimit(100L)
                .build();

        Iterable<Session> sessions;
        try {
            sessions = stripeClient.checkout().sessions().list(params).autoPagingIterable();
        } catch (StripeException e) {
            log.error("Failed to list Stripe checkout sessions for reconciliation", e);
            return;
        }

        for (Session session : sessions) {
            try {
                checkSessionHasMatchingLedgerEntry(session);
            } catch (Exception e) {
                log.error("Failed to reconcile Stripe session {}", session.getId(), e);
            }
        }
    }

    private void checkSessionHasMatchingLedgerEntry(Session session) {
        if (!"paid".equals(session.getPaymentStatus())) {
            return;
        }
        Map<String, String> metadata = session.getMetadata();
        String idempotencyKey = metadata == null ? null : metadata.get("idempotencyKey");
        if (idempotencyKey == null) {
            return;
        }

        BigDecimal expected = StripeAmounts.toDecimal(session.getAmountTotal(), session.getCurrency());
        Optional<IdempotencyRecord> record = idempotencyDao.findByKey(idempotencyKey);
        if (record.isEmpty() || record.get().status() != IdempotencyStatus.SUCCEEDED || record.get().transactionId() == null) {
            reconciliationMismatchDao.insert(PROCESSOR, session.getId(), ReconciliationMismatchType.MISSING_LEDGER_ENTRY, expected, null);
            return;
        }

        LedgerTransaction transaction = ledgerTransactionDao.findById(record.get().transactionId()).orElseThrow();
        if (transaction.amount().compareTo(expected) != 0) {
            reconciliationMismatchDao.insert(PROCESSOR, session.getId(), ReconciliationMismatchType.AMOUNT_MISMATCH, expected, transaction.amount());
        }
    }

    private void reconcileLedgerAgainstStripe(Instant since) {
        for (ProcessedWebhookEvent event : processedWebhookEventDao.findProcessedSince(PROCESSOR, since)) {
            try {
                checkProcessedEventStillValidInStripe(event);
            } catch (Exception e) {
                log.error("Failed to reconcile processed webhook event {}", event.eventId(), e);
            }
        }
    }

    private void checkProcessedEventStillValidInStripe(ProcessedWebhookEvent event) {
        JsonNode sessionNode = objectMapper.readTree(event.rawPayload()).path("data").path("object");
        String sessionId = sessionNode.path("id").asString();
        BigDecimal recordedAmount = StripeAmounts.toDecimal(sessionNode.path("amount_total").asLong(), sessionNode.path("currency").asString());

        Session session;
        try {
            session = stripeClient.checkout().sessions().retrieve(sessionId);
        } catch (InvalidRequestException e) {
            reconciliationMismatchDao.insert(PROCESSOR, event.eventId(), ReconciliationMismatchType.MISSING_PROCESSOR_RECORD, recordedAmount, null);
            return;
        } catch (StripeException e) {
            log.error("Could not verify Stripe session {} for reconciliation", sessionId, e);
            return;
        }

        BigDecimal currentAmount = StripeAmounts.toDecimal(session.getAmountTotal(), session.getCurrency());
        if (!"paid".equals(session.getPaymentStatus()) || currentAmount.compareTo(recordedAmount) != 0) {
            reconciliationMismatchDao.insert(PROCESSOR, event.eventId(), ReconciliationMismatchType.AMOUNT_MISMATCH, recordedAmount, currentAmount);
        }
    }
}
