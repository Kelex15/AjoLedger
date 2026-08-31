package com.ajoledger.backend.circle.exception;

public class InviteAlreadyRedeemedException extends RuntimeException {
    public InviteAlreadyRedeemedException(String message) {
        super(message);
    }
}
