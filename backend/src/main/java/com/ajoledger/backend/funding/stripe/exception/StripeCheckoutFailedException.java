package com.ajoledger.backend.funding.stripe.exception;

public class StripeCheckoutFailedException extends RuntimeException {
    public StripeCheckoutFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
