package com.ajoledger.backend.circle.exception;

public class CircleActionNotAllowedException extends RuntimeException {
    public CircleActionNotAllowedException(String message) {
        super(message);
    }
}
