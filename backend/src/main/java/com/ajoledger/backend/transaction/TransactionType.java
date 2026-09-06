package com.ajoledger.backend.transaction;

public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
    CONTRIBUTION,
    CONTRIBUTION_REFUND,
    PAYOUT,
    LIEN_HOLD,
    LIEN_WITHDRAWAL,
    LIEN_SEIZURE,
    BUFFER_PAYOUT_TO_COVER_DEFAULT,
    CIRCLE_RESERVE_DISBURSEMENT
}
