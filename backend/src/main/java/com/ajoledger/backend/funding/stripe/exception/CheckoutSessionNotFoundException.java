package com.ajoledger.backend.funding.stripe.exception;

/** Thrown both when Stripe has no such session and when it exists but belongs to a different
 * user, deliberately indistinguishable so a caller can't probe for valid session ids. */
public class CheckoutSessionNotFoundException extends RuntimeException {
    public CheckoutSessionNotFoundException(String sessionId) {
        super("No checkout session found for id " + sessionId);
    }
}
