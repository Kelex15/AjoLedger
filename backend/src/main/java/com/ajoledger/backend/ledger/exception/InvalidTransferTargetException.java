package com.ajoledger.backend.ledger.exception;

public class InvalidTransferTargetException extends RuntimeException {
    public InvalidTransferTargetException(String message) {
        super(message);
    }
}
