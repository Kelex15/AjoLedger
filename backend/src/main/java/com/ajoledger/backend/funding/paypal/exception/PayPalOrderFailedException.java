package com.ajoledger.backend.funding.paypal.exception;

public class PayPalOrderFailedException extends RuntimeException {

    public PayPalOrderFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
