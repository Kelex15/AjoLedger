package com.ajoledger.backend.transaction;

import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.account.entity.UserAccount;
import com.ajoledger.backend.account.exception.AccountNotFoundException;
import com.ajoledger.backend.account.repository.UserAccountRepository;
import com.ajoledger.backend.ledger.LedgerDao;
import com.ajoledger.backend.ledger.LedgerEntry;
import com.ajoledger.backend.ledger.exception.InsufficientBalanceException;
import com.ajoledger.backend.transaction.exception.IdempotencyKeyConflictException;
import com.ajoledger.backend.transaction.idempotency.IdempotencyDao;
import com.ajoledger.backend.transaction.idempotency.IdempotencyRecord;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionTemplate transactionTemplate;
    private final LedgerDao ledgerDao;
    private final TransactionDao transactionDao;
    private final IdempotencyDao idempotencyDao;
    private final UserAccountRepository userAccountRepository;

    public Transaction postTransfer(UUID fromUserId, UserAccountType fromType, UUID toUserId, UserAccountType toType,
                                     BigDecimal amount, String idempotencyKey) {
        UserAccount from = userAccountRepository.requireByUserIdAndType(fromUserId, fromType);
        UserAccount to = userAccountRepository.requireByUserIdAndType(toUserId, toType);
        List<Leg> legs = List.of(
                new Leg(from.getId(), amount, BigDecimal.ZERO),
                new Leg(to.getId(), BigDecimal.ZERO, amount));
        return postLedgerTransaction(idempotencyKey, "TRANSFER", TransactionType.TRANSFER, amount, "Transfer", legs);
    }

    /** Empty both when the transaction doesn't exist and when it exists but the user wasn't a
     * party to it. A non-participant shouldn't be able to tell those two cases apart. */
    public Optional<Transaction> findTransactionForUser(UUID transactionId, UUID userId) {
        Optional<Transaction> transaction = transactionDao.findById(transactionId);
        if (transaction.isEmpty() || !ledgerDao.transactionInvolvesUser(transactionId, userId)) {
            return Optional.empty();
        }
        return transaction;
    }

    /** Shared by every domain's money-moving service, not just this one. */
    public Transaction postLedgerTransaction(String idempotencyKey, String requestType, TransactionType type,
                                              BigDecimal amount, String description, List<Leg> legs) {
        return postLedgerTransaction(idempotencyKey, requestType, type, amount, description, legs, t -> {
        });
    }

    public Transaction postLedgerTransaction(String idempotencyKey, String requestType, TransactionType type,
                                              BigDecimal amount, String description, List<Leg> legs,
                                              Consumer<Transaction> onSuccess) {
        String requestHash = hashRequest(requestType, legs);
        UUID idempotencyId = UUID.randomUUID();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();

        Transaction result = transactionTemplate.execute(status -> {
            Optional<IdempotencyRecord> existing = idempotencyDao.claim(idempotencyId, idempotencyKey, requestType, requestHash);
            if (existing.isPresent()) {
                IdempotencyRecord record = existing.get();
                if (!record.requestHash().equals(requestHash)) {
                    failure.set(new IdempotencyKeyConflictException("Idempotency key reused with a different request"));
                    return null;
                }
                return switch (record.status()) {
                    case SUCCEEDED -> transactionDao.findById(record.transactionId()).orElseThrow();
                    case IN_PROGRESS, FAILED -> {
                        failure.set(new IdempotencyKeyConflictException("Idempotency key already used: " + idempotencyKey));
                        yield null;
                    }
                };
            }

            List<UUID> accountIds = legs.stream().map(Leg::accountId).toList();
            Map<UUID, LedgerDao.AccountBalance> balances = ledgerDao.lockAccounts(accountIds);

            List<UUID> missingAccountIds = accountIds.stream().distinct()
                    .filter(id -> !balances.containsKey(id))
                    .toList();
            if (!missingAccountIds.isEmpty()) {
                throw new AccountNotFoundException("No account found for id(s): " + missingAccountIds);
            }

            boolean insufficient = legs.stream().anyMatch(leg -> {
                LedgerDao.AccountBalance account = balances.get(leg.accountId());
                return leg.debitAmount().signum() > 0 && !account.unlimited()
                        && account.balance().compareTo(leg.debitAmount()) < 0;
            });

            if (insufficient) {
                idempotencyDao.markFailed(idempotencyId, "Insufficient balance");
                failure.set(new InsufficientBalanceException("Insufficient balance for one or more accounts"));
                return null;
            }

            Instant now = Instant.now();
            Transaction transaction = new Transaction(UUID.randomUUID(), amount, description, type, now);
            transactionDao.insertTransaction(transaction);

            List<LedgerEntry> entries = legs.stream()
                    .map(leg -> new LedgerEntry(UUID.randomUUID(), leg.accountId(), leg.debitAmount(),
                            leg.creditAmount(), transaction.id(), now))
                    .toList();
            ledgerDao.insertEntries(entries);

            Map<UUID, BigDecimal> netChanges = legs.stream()
                    .collect(Collectors.toMap(Leg::accountId,
                            leg -> leg.creditAmount().subtract(leg.debitAmount()), BigDecimal::add));
            ledgerDao.adjustBalances(netChanges);

            idempotencyDao.markSucceeded(idempotencyId, transaction.id());
            onSuccess.accept(transaction);
            return transaction;
        });

        if (failure.get() != null) {
            throw failure.get();
        }
        return result;
    }

    private String hashRequest(String requestType, List<Leg> legs) {
        String canonical = requestType + "|" + legs.stream()
                .map(leg -> leg.accountId() + ":" + leg.debitAmount() + ":" + leg.creditAmount())
                .collect(Collectors.joining(","));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
