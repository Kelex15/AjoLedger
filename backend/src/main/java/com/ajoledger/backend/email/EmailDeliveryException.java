package com.ajoledger.backend.email;

import lombok.Getter;

@Getter
public class EmailDeliveryException extends RuntimeException {

    private final boolean retryable;

    public EmailDeliveryException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }
}
