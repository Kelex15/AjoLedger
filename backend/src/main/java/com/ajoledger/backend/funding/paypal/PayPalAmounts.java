package com.ajoledger.backend.funding.paypal;

import com.ajoledger.backend.account.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** PayPal carries amounts as a decimal string alongside a currency code, and expects exactly the
 * currency's decimal places. Shared by every path that reads or writes a PayPal amount. */
final class PayPalAmounts {

    private PayPalAmounts() {
    }

    static String toValue(BigDecimal amount, Currency currency) {
        return amount.setScale(currency.getDecimalScale(), RoundingMode.UNNECESSARY).toPlainString();
    }

    static BigDecimal toDecimal(String value) {
        return new BigDecimal(value);
    }
}
