package com.ajoledger.backend.funding.paypal;

import com.paypal.sdk.PaypalServerSdkClient;
import com.paypal.sdk.exceptions.ApiException;
import com.paypal.sdk.models.AmountWithBreakdown;
import com.paypal.sdk.models.CaptureOrderInput;
import com.paypal.sdk.models.CaptureStatus;
import com.paypal.sdk.models.CheckoutPaymentIntent;
import com.paypal.sdk.models.CreateOrderInput;
import com.paypal.sdk.models.GetOrderInput;
import com.paypal.sdk.models.LinkDescription;
import com.paypal.sdk.models.Order;
import com.paypal.sdk.models.OrderApplicationContext;
import com.paypal.sdk.models.OrderRequest;
import com.paypal.sdk.models.OrderStatus;
import com.paypal.sdk.models.PurchaseUnit;
import com.paypal.sdk.models.PurchaseUnitRequest;
import io.apimatic.core.exceptions.AuthValidationException;

import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.account.entity.UserAccount;
import com.ajoledger.backend.account.repository.UserAccountRepository;
import com.ajoledger.backend.funding.DepositProvider;
import com.ajoledger.backend.funding.FundingService;
import com.ajoledger.backend.funding.FundingType;
import com.ajoledger.backend.funding.paypal.exception.PayPalOrderFailedException;
import com.ajoledger.backend.funding.paypal.exception.PayPalOrderNotFoundException;
import com.ajoledger.backend.funding.processor.ReconciliationMismatchDao;
import com.ajoledger.backend.funding.processor.ReconciliationMismatchType;
import com.ajoledger.backend.ledger.model.LedgerTransaction;
import com.ajoledger.backend.system.AppProperties;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayPalOrderService implements DepositProvider {

    private static final Logger log = LoggerFactory.getLogger(PayPalOrderService.class);
    static final String PROCESSOR = "PAYPAL";

    private final PaypalServerSdkClient paypalClient;
    private final AppProperties appProperties;
    private final UserAccountRepository userAccountRepository;
    private final PayPalOrderDao payPalOrderDao;
    private final FundingService fundingService;
    private final ReconciliationMismatchDao reconciliationMismatchDao;

    @Override
    public FundingType fundingType() {
        return FundingType.PAYPAL;
    }

    /** The local record is written before this returns, so a failure to record it means the caller
     * never gets a redirect and the order is left to expire unapproved. */
    @Override
    public String createDeposit(UUID userId, BigDecimal amount, String idempotencyKey) {
        UserAccount checkingAccount = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.CHECKING);
        Currency currency = checkingAccount.getCurrency();
        String frontendBaseUrl = appProperties.frontend().baseUrl();

        OrderRequest orderRequest = new OrderRequest.Builder()
                .intent(CheckoutPaymentIntent.CAPTURE)
                .purchaseUnits(List.of(new PurchaseUnitRequest.Builder()
                        .amount(new AmountWithBreakdown.Builder(
                                currency.name(), PayPalAmounts.toValue(amount, currency)).build())
                        .description("AjoLedger wallet deposit")
                        .customId(userId.toString())
                        .build()))
                .applicationContext(new OrderApplicationContext.Builder()
                        .brandName("AjoLedger")
                        .returnUrl(frontendBaseUrl + "/?depositStatus=success&provider=paypal")
                        .cancelUrl(frontendBaseUrl + "/?depositStatus=cancelled&provider=paypal")
                        .build())
                .build();

        CreateOrderInput input = new CreateOrderInput.Builder()
                .body(orderRequest)
                .paypalRequestId(idempotencyKey)
                .build();

        Order order;
        try {
            order = paypalClient.getOrdersController().createOrder(input).getResult();
        } catch (ApiException | IOException | AuthValidationException e) {
            throw new PayPalOrderFailedException("Failed to create PayPal order", e);
        }

        payPalOrderDao.insert(UUID.randomUUID(), order.getId(), userId, idempotencyKey, amount, currency);
        return approveUrl(order);
    }

    @Override
    public Optional<LedgerTransaction> settle(UUID currentUserId, String orderId) {
        boolean ownedByCaller = payPalOrderDao.findByOrderId(orderId)
                .filter(order -> order.userId().equals(currentUserId))
                .isPresent();
        if (!ownedByCaller) {
            throw new PayPalOrderNotFoundException(orderId);
        }
        return settleOrder(orderId);
    }

    /** Shared by both webhook events and the checkout-return path. Captures an approved order and
     * posts the deposit for a captured one; the PayPal request id and the ledger idempotency key
     * make it safe for all three to arrive in any order, or at once. */
    public Optional<LedgerTransaction> settleOrder(String orderId) {
        Optional<PayPalOrder> record = payPalOrderDao.findByOrderId(orderId);
        if (record.isEmpty()) {
            log.info("No local record for PayPal order {}, ignoring", orderId);
            return Optional.empty();
        }
        PayPalOrder localOrder = record.get();

        Order order = retrieveOrder(orderId);
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.APPROVED) {
            order = capture(orderId, localOrder.idempotencyKey());
            status = order.getStatus();
        }

        if (status != OrderStatus.COMPLETED) {
            log.info("PayPal order {} has status {}, not settling yet", orderId, status);
            return Optional.empty();
        }

        return capturedAmount(order).map(amount -> {
            recordAmountDiscrepancy(localOrder, amount);
            return fundingService.postDeposit(localOrder.userId(), UserAccountType.CHECKING,
                    FundingType.PAYPAL, amount, localOrder.idempotencyKey());
        });
    }

    /** The ledger credits what PayPal actually took, so a capture for a different amount than we
     * asked for still posts. Both numbers are already in hand here, so the discrepancy is recorded
     * now rather than left for the next reconciliation run to notice. */
    private void recordAmountDiscrepancy(PayPalOrder localOrder, BigDecimal capturedAmount) {
        if (capturedAmount.compareTo(localOrder.amount()) == 0) {
            return;
        }
        log.error("PayPal order {} captured {} but {} was requested", localOrder.orderId(), capturedAmount, localOrder.amount());
        reconciliationMismatchDao.insert(PROCESSOR, localOrder.orderId(), ReconciliationMismatchType.AMOUNT_MISMATCH,
                localOrder.amount(), capturedAmount);
    }

    public Order retrieveOrder(String orderId) {
        try {
            return paypalClient.getOrdersController()
                    .getOrder(new GetOrderInput.Builder(orderId).build())
                    .getResult();
        } catch (ApiException e) {
            if (e.getResponseCode() == 404) {
                throw new PayPalOrderNotFoundException(orderId);
            }
            throw new PayPalOrderFailedException("Failed to retrieve PayPal order " + orderId, e);
        } catch (IOException | AuthValidationException e) {
            throw new PayPalOrderFailedException("Failed to retrieve PayPal order " + orderId, e);
        }
    }

    private Order capture(String orderId, String idempotencyKey) {
        CaptureOrderInput input = new CaptureOrderInput.Builder()
                .id(orderId)
                .paypalRequestId(idempotencyKey)
                .build();
        try {
            return paypalClient.getOrdersController().captureOrder(input).getResult();
        } catch (ApiException | IOException | AuthValidationException e) {
            throw new PayPalOrderFailedException("Failed to capture PayPal order " + orderId, e);
        }
    }

    /** What PayPal actually took, which is what the ledger credits and what reconciliation compares
     * against. Empty when the order carries no completed capture. */
    static Optional<BigDecimal> capturedAmount(Order order) {
        List<PurchaseUnit> purchaseUnits = order.getPurchaseUnits();
        if (purchaseUnits == null || purchaseUnits.isEmpty()) {
            return Optional.empty();
        }
        PurchaseUnit purchaseUnit = purchaseUnits.getFirst();
        if (purchaseUnit.getPayments() == null || purchaseUnit.getPayments().getCaptures() == null) {
            return Optional.empty();
        }
        return purchaseUnit.getPayments().getCaptures().stream()
                .filter(capture -> capture.getStatus() == CaptureStatus.COMPLETED)
                .findFirst()
                .map(capture -> PayPalAmounts.toDecimal(capture.getAmount().getValue()));
    }

    private String approveUrl(Order order) {
        List<LinkDescription> links = order.getLinks() == null ? List.of() : order.getLinks();
        return links.stream()
                .filter(link -> "approve".equalsIgnoreCase(link.getRel()) || "payer-action".equalsIgnoreCase(link.getRel()))
                .map(LinkDescription::getHref)
                .findFirst()
                .orElseThrow(() -> new PayPalOrderFailedException(
                        "PayPal order " + order.getId() + " has no approval link", null));
    }
}
