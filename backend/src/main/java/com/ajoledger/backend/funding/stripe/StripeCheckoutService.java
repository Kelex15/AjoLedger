package com.ajoledger.backend.funding.stripe;

import com.stripe.StripeClient;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;

import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.account.entity.UserAccount;
import com.ajoledger.backend.account.repository.UserAccountRepository;
import com.ajoledger.backend.funding.DepositProvider;
import com.ajoledger.backend.funding.FundingType;
import com.ajoledger.backend.funding.stripe.exception.CheckoutSessionNotFoundException;
import com.ajoledger.backend.funding.stripe.exception.StripeCheckoutFailedException;
import com.ajoledger.backend.ledger.model.LedgerTransaction;
import com.ajoledger.backend.system.AppProperties;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StripeCheckoutService implements DepositProvider {

    private final StripeClient stripeClient;
    private final AppProperties appProperties;
    private final UserAccountRepository userAccountRepository;
    private final StripeCheckoutProcessor checkoutProcessor;

    @Override
    public FundingType fundingType() {
        return FundingType.STRIPE;
    }

    @Override
    public String createDeposit(UUID userId, BigDecimal amount, String idempotencyKey) {
        UserAccount checkingAccount = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.CHECKING);
        Currency currency = checkingAccount.getCurrency();
        String frontendBaseUrl = appProperties.frontend().baseUrl();
        long unitAmount = amount.movePointRight(currency.getDecimalScale())
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(frontendBaseUrl + "/?depositStatus=success&session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontendBaseUrl + "/?depositStatus=cancelled")
                .putMetadata("userId", userId.toString())
                .putMetadata("idempotencyKey", idempotencyKey)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currency.name().toLowerCase())
                                .setUnitAmount(unitAmount)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("AjoLedger wallet deposit")
                                        .build())
                                .build())
                        .build())
                .build();

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        try {
            Session session = stripeClient.checkout().sessions().create(params, requestOptions);
            return session.getUrl();
        } catch (StripeException e) {
            throw new StripeCheckoutFailedException("Failed to create Stripe checkout session", e);
        }
    }

    /** Fetches the session fresh from Stripe rather than trusting anything the caller sent, so it
     * also covers a webhook that never arrives at all, not just one still in flight. */
    @Override
    public Optional<LedgerTransaction> settle(UUID currentUserId, String sessionId) {
        Session session = retrieveOwnedSession(currentUserId, sessionId);
        String currencyCode = session.getCurrency();
        PaidCheckoutSession paidSession = new PaidCheckoutSession(
                session.getId(),
                session.getPaymentStatus(),
                currentUserId,
                session.getMetadata().get("idempotencyKey"),
                StripeAmounts.toDecimal(session.getAmountTotal(), currencyCode),
                Currency.valueOf(currencyCode.toUpperCase()));
        return checkoutProcessor.processIfPaid(paidSession);
    }

    private Session retrieveOwnedSession(UUID currentUserId, String sessionId) {
        Session session;
        try {
            session = stripeClient.checkout().sessions().retrieve(sessionId);
        } catch (InvalidRequestException e) {
            throw new CheckoutSessionNotFoundException(sessionId);
        } catch (StripeException e) {
            throw new StripeCheckoutFailedException("Failed to retrieve Stripe checkout session", e);
        }

        Map<String, String> metadata = session.getMetadata();
        String ownerId = metadata == null ? null : metadata.get("userId");
        if (!currentUserId.toString().equals(ownerId)) {
            throw new CheckoutSessionNotFoundException(sessionId);
        }
        return session;
    }
}
