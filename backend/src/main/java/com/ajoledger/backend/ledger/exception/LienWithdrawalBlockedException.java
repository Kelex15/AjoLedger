package com.ajoledger.backend.ledger.exception;

public class LienWithdrawalBlockedException extends RuntimeException {
    public LienWithdrawalBlockedException(String message) {
        super(message);
    }
}
