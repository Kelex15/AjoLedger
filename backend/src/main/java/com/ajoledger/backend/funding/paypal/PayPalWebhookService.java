package com.ajoledger.backend.funding.paypal;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.ajoledger.backend.funding.processor.ProcessedWebhookEvent;
import com.ajoledger.backend.funding.processor.ProcessedWebhookEventDao;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

/** Only the event id and the order id are read off the payload; the order itself is refetched from
 * PayPal, so a forged event can do no more than cause a lookup. */
@Service
@RequiredArgsConstructor
public class PayPalWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PayPalWebhookService.class);
    private static final String PROCESSOR = PayPalOrderService.PROCESSOR;
    private static final String ORDER_APPROVED = "CHECKOUT.ORDER.APPROVED";
    private static final String CAPTURE_COMPLETED = "PAYMENT.CAPTURE.COMPLETED";

    private final ProcessedWebhookEventDao processedWebhookEventDao;
    private final PayPalOrderService payPalOrderService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public void handle(String rawPayload) {
        JsonNode event = readEvent(rawPayload);
        String eventId = event.path("id").asString(null);
        String eventType = event.path("event_type").asString(null);
        if (eventId == null) {
            log.warn("Ignoring PayPal webhook with no event id");
            return;
        }

        if (!ORDER_APPROVED.equals(eventType) && !CAPTURE_COMPLETED.equals(eventType)) {
            log.debug("Ignoring PayPal event {} of type {}", eventId, eventType);
            return;
        }

        String orderId = orderIdFrom(eventType, event.path("resource"));
        if (orderId == null) {
            log.warn("PayPal event {} of type {} carries no order id", eventId, eventType);
            return;
        }

        Optional<ProcessedWebhookEvent> existing = processedWebhookEventDao.findByProcessorAndEventId(PROCESSOR, eventId);
        if (existing.isPresent() && existing.get().processedAt() != null) {
            log.info("PayPal event {} already processed, ignoring redelivery", eventId);
            return;
        }

        UUID rowId = existing.map(ProcessedWebhookEvent::id).orElseGet(UUID::randomUUID);
        if (existing.isEmpty()) {
            processedWebhookEventDao.recordReceived(rowId, PROCESSOR, eventId, rawPayload);
        }

        transactionTemplate.execute(status -> {
            payPalOrderService.settleOrder(orderId)
                    .ifPresent(transaction -> processedWebhookEventDao.markProcessed(rowId, transaction.id()));
            return null;
        });
    }

    /** An approved-order event carries the order as its resource; a capture event carries the
     * capture, with the order it belongs to only in the related ids. */
    private String orderIdFrom(String eventType, JsonNode resource) {
        if (ORDER_APPROVED.equals(eventType)) {
            return resource.path("id").asString(null);
        }
        return resource.path("supplementary_data").path("related_ids").path("order_id").asString(null);
    }

    private JsonNode readEvent(String rawPayload) {
        try {
            return objectMapper.readTree(rawPayload);
        } catch (Exception e) {
            throw new IllegalStateException("Malformed PayPal webhook payload", e);
        }
    }
}
