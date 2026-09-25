package com.ajoledger.backend.funding.paypal.exception;

public class PayPalOrderNotFoundException extends RuntimeException {

    public PayPalOrderNotFoundException(String orderId) {
        super("PayPal order not found: " + orderId);
    }
}
