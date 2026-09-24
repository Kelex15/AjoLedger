package com.ajoledger.backend.funding.stripe;

import com.ajoledger.backend.account.Currency;

import java.math.BigDecimal;

/** Stripe reports amounts as an integer in the currency's smallest unit. Shared by every path
 * that reads a Stripe checkout session, so the conversion doesn't drift between them. */
final class StripeAmounts {

    private StripeAmounts() {
    }

    static BigDecimal toDecimal(long minorUnits, String currencyCode) {
        Currency currency = Currency.valueOf(currencyCode.toUpperCase());
        return BigDecimal.valueOf(minorUnits).movePointLeft(currency.getDecimalScale());
    }
}
