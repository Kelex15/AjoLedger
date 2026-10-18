package com.ajoledger.backend.ledger.model;

import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.ledger.LedgerTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One ledger entry as it appears on a user's statement, scoped to the account it touched, so a
 * transfer between two of their own accounts shows up as the two lines it actually is.
 * Amount is signed against that account: credits positive, debits negative. Circle name is the
 * circle on the other side of the transaction, if any; round number is set only for payouts. */
public record LedgerActivity(
    UUID entryId,
    UUID transactionId,
    UserAccountType accountType,
    String accountNumber,
    BigDecimal amount,
    Currency currency,
    String description,
    LedgerTransactionType transactionType,
    String circleName,
    Integer roundNumber,
    Instant createdAt
) {

}
