package com.ajoledger.backend.transaction;

public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
    CONTRIBUTION,
    PAYOUT,
    LIEN_HOLD,
    LIEN_WITHDRAWAL,
    LIEN_SEIZURE,
    BUFFER_PAYOUT_TO_COVER_DEFAULT,
    GROUP_SHORTFALL_ABSORPTION
}
