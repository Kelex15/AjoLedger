package com.ajoledger.backend.funding.stripe;

public enum ReconciliationMismatchType {
    MISSING_LEDGER_ENTRY,
    MISSING_PROCESSOR_RECORD,
    AMOUNT_MISMATCH
}
