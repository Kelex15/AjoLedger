package com.ajoledger.backend.ledger;

import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.account.UserAccountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One ledger entry as it appears on a user's statement, scoped to the account it touched, so a
 * transfer between two of their own accounts shows up as the two lines it actually is.
 * Amount is signed against that account: credits positive, debits negative. */
public record LedgerActivity(
    UUID entryId,
    UUID transactionId,
    UserAccountType accountType,
    String accountNumber,
    BigDecimal amount,
    Currency currency,
    String description,
    LedgerTransactionType transactionType,
    Instant createdAt
) {

}
