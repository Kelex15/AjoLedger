package com.ajoledger.backend.funding.processor;

public enum ReconciliationMismatchType {
    MISSING_LEDGER_ENTRY,
    MISSING_PROCESSOR_RECORD,
    AMOUNT_MISMATCH,
    /** Buyer approved but capture never happened, so the authorization expires with no money taken. */
    UNCAPTURED_ORDER
}
