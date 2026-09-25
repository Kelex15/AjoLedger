package com.ajoledger.backend.funding.stripe;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;

import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.funding.processor.ProcessedWebhookEvent;
import com.ajoledger.backend.funding.processor.ProcessedWebhookEventDao;
import com.ajoledger.backend.system.AppProperties;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

/** Signature verification and the event's id/type come from the Stripe SDK; everything else is
 * read straight off the raw JSON instead of the SDK's typed data-object deserializer, which is
 * sensitive to API-version skew between this SDK and the Stripe account's configured version. */
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);
    private static final String PROCESSOR = "STRIPE";
    private static final String CHECKOUT_SESSION_COMPLETED = "checkout.session.completed";

    private final AppProperties appProperties;
    private final ProcessedWebhookEventDao processedWebhookEventDao;
    private final StripeCheckoutProcessor checkoutProcessor;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public void handle(String rawPayload, String signatureHeader) throws SignatureVerificationException {
        Event event = parseAndVerify(rawPayload, signatureHeader);

        if (!CHECKOUT_SESSION_COMPLETED.equals(event.getType())) {
            log.debug("Ignoring Stripe event {} of type {}", event.getId(), event.getType());
            return;
        }

        Optional<ProcessedWebhookEvent> existing = processedWebhookEventDao.findByProcessorAndEventId(PROCESSOR, event.getId());
        if (existing.isPresent() && existing.get().processedAt() != null) {
            log.info("Stripe event {} already processed, ignoring redelivery", event.getId());
            return;
        }

        // Committed on its own, deliberately outside the transaction below, so this durable
        // "we received it" record survives even if processing fails right after.
        UUID rowId = existing.map(ProcessedWebhookEvent::id).orElseGet(UUID::randomUUID);
        if (existing.isEmpty()) {
            processedWebhookEventDao.recordReceived(rowId, PROCESSOR, event.getId(), rawPayload);
        }

        processCheckoutSessionCompleted(rowId, rawPayload);
    }

    private void processCheckoutSessionCompleted(UUID rowId, String rawPayload) {
        JsonNode sessionNode = readSessionNode(rawPayload);
        String currencyCode = sessionNode.path("currency").asString();
        PaidCheckoutSession session = new PaidCheckoutSession(
                sessionNode.path("id").asString(null),
                sessionNode.path("payment_status").asString(null),
                UUID.fromString(sessionNode.path("metadata").path("userId").asString()),
                sessionNode.path("metadata").path("idempotencyKey").asString(),
                StripeAmounts.toDecimal(sessionNode.path("amount_total").asLong(), currencyCode),
                Currency.valueOf(currencyCode.toUpperCase()));

        transactionTemplate.execute(status -> {
            checkoutProcessor.processIfPaid(session)
                    .ifPresent(transaction -> processedWebhookEventDao.markProcessed(rowId, transaction.id()));
            return null;
        });
    }

    private Event parseAndVerify(String rawPayload, String signatureHeader) throws SignatureVerificationException {
        try {
            return Webhook.constructEvent(rawPayload, signatureHeader, appProperties.stripe().webhookSecret());
        } catch (SignatureVerificationException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Rejecting unparseable Stripe webhook payload", e);
            throw new SignatureVerificationException("Malformed webhook payload", signatureHeader);
        }
    }

    private JsonNode readSessionNode(String rawPayload) {
        try {
            return objectMapper.readTree(rawPayload).path("data").path("object");
        } catch (Exception e) {
            throw new IllegalStateException("Malformed Stripe webhook payload", e);
        }
    }
}
