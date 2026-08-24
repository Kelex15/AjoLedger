package com.ajoledger.backend.transaction.exception;

public class LienWithdrawalBlockedException extends RuntimeException {
    public LienWithdrawalBlockedException(String message) {
        super(message);
    }
}
