package com.ajoledger.backend.funding.stripe;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;

import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.account.entity.UserAccount;
import com.ajoledger.backend.account.repository.UserAccountRepository;
import com.ajoledger.backend.funding.stripe.exception.StripeCheckoutFailedException;
import com.ajoledger.backend.system.AppProperties;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StripeCheckoutService {

    private final StripeClient stripeClient;
    private final AppProperties appProperties;
    private final UserAccountRepository userAccountRepository;

    public String createDepositCheckoutSession(UUID userId, BigDecimal amount, String idempotencyKey) {
        UserAccount checkingAccount = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.CHECKING);
        Currency currency = checkingAccount.getCurrency();
        String frontendBaseUrl = appProperties.frontend().baseUrl();
        long unitAmount = amount.movePointRight(currency.getDecimalScale())
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(frontendBaseUrl + "/?depositStatus=success")
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
}
