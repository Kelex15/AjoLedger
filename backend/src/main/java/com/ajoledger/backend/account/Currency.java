package com.ajoledger.backend.account;

public enum Currency {
    USD(2),
    EUR(2),
    GBP(2),
    CAD(2);

    private final int decimalScale;

    Currency(int decimalScale) {
        this.decimalScale = decimalScale;
    }

    public int getDecimalScale() {
        return decimalScale;
    }
}
