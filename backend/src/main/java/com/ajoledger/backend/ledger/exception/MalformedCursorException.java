package com.ajoledger.backend.ledger.exception;

public class MalformedCursorException extends RuntimeException {
    public MalformedCursorException(String message) {
        super(message);
    }
}
