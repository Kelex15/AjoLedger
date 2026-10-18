package com.ajoledger.backend.funding.paypal;

import com.paypal.sdk.models.Order;
import com.paypal.sdk.models.OrderStatus;

import com.ajoledger.backend.funding.paypal.exception.PayPalOrderNotFoundException;
import com.ajoledger.backend.funding.processor.ReconciliationMismatchDao;
import com.ajoledger.backend.funding.processor.ReconciliationMismatchType;
import com.ajoledger.backend.ledger.dao.LedgerTransactionDao;
import com.ajoledger.backend.ledger.model.LedgerTransaction;
import com.ajoledger.backend.ledger.idempotency.IdempotencyDao;
import com.ajoledger.backend.ledger.idempotency.IdempotencyRecord;
import com.ajoledger.backend.ledger.idempotency.IdempotencyStatus;
import com.ajoledger.backend.system.AppProperties;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Orders v2 has no list endpoint, so the locally recorded orders are the set to reconcile against
 * rather than a processor-side listing. Observes and records only; it never captures or posts. */
@Service
@RequiredArgsConstructor
public class PayPalReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(PayPalReconciliationService.class);
    private static final String PROCESSOR = PayPalOrderService.PROCESSOR;

    private final PayPalOrderDao payPalOrderDao;
    private final PayPalOrderService payPalOrderService;
    private final IdempotencyDao idempotencyDao;
    private final LedgerTransactionDao ledgerTransactionDao;
    private final ReconciliationMismatchDao reconciliationMismatchDao;
    private final AppProperties appProperties;

    public void reconcile(Instant since) {
        for (PayPalOrder localOrder : payPalOrderDao.findCreatedSince(since)) {
            try {
                check(localOrder);
            } catch (Exception e) {
                log.error("Failed to reconcile PayPal order {}", localOrder.orderId(), e);
            }
        }
    }

    private void check(PayPalOrder localOrder) {
        Order order;
        try {
            order = payPalOrderService.retrieveOrder(localOrder.orderId());
        } catch (PayPalOrderNotFoundException e) {
            reconciliationMismatchDao.insert(PROCESSOR, localOrder.orderId(),
                    ReconciliationMismatchType.MISSING_PROCESSOR_RECORD, localOrder.amount(), null);
            return;
        }

        OrderStatus status = order.getStatus();
        if (status == OrderStatus.COMPLETED) {
            PayPalOrderService.capturedAmount(order).ifPresent(captured -> checkLedgerMatchesCapture(localOrder, captured));
        } else if (status == OrderStatus.APPROVED && approvedTooLongAgo(localOrder)) {
            log.error("PayPal order {} was approved but never captured", localOrder.orderId());
            reconciliationMismatchDao.insert(PROCESSOR, localOrder.orderId(),
                    ReconciliationMismatchType.UNCAPTURED_ORDER, localOrder.amount(), null);
        }
    }

    /** Compared against what PayPal captured rather than what was requested, since the ledger
     * credits the captured amount and a difference between the two is already recorded at settle. */
    private void checkLedgerMatchesCapture(PayPalOrder localOrder, BigDecimal capturedAmount) {
        Optional<IdempotencyRecord> record = idempotencyDao.findByKey(localOrder.idempotencyKey());
        if (record.isEmpty() || record.get().status() != IdempotencyStatus.SUCCEEDED || record.get().transactionId() == null) {
            reconciliationMismatchDao.insert(PROCESSOR, localOrder.orderId(),
                    ReconciliationMismatchType.MISSING_LEDGER_ENTRY, capturedAmount, null);
            return;
        }

        LedgerTransaction transaction = ledgerTransactionDao.findById(record.get().transactionId()).orElseThrow();
        if (transaction.amount().compareTo(capturedAmount) != 0) {
            reconciliationMismatchDao.insert(PROCESSOR, localOrder.orderId(),
                    ReconciliationMismatchType.AMOUNT_MISMATCH, capturedAmount, transaction.amount());
        }
    }

    /** A capture still in flight looks identical to one that never happened, so an order only
     * counts as uncaptured once it is older than the window a legitimate capture fits in. */
    private boolean approvedTooLongAgo(PayPalOrder localOrder) {
        Duration grace = Duration.ofMinutes(appProperties.paypal().uncapturedGraceMinutes());
        return localOrder.createdAt().isBefore(Instant.now().minus(grace));
    }
}
