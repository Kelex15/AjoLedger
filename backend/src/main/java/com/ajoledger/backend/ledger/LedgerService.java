package com.ajoledger.backend.ledger;

import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.account.entity.UserAccount;
import com.ajoledger.backend.account.exception.AccountNotFoundException;
import com.ajoledger.backend.account.repository.UserAccountRepository;
import com.ajoledger.backend.ledger.exception.InsufficientBalanceException;
import com.ajoledger.backend.ledger.exception.IdempotencyKeyConflictException;
import com.ajoledger.backend.ledger.idempotency.IdempotencyDao;
import com.ajoledger.backend.ledger.idempotency.IdempotencyRecord;

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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final TransactionTemplate transactionTemplate;
    private final LedgerDao ledgerDao;
    private final LedgerTransactionDao ledgerTransactionDao;
    private final LedgerOperationDao ledgerOperationDao;
    private final IdempotencyDao idempotencyDao;
    private final UserAccountRepository userAccountRepository;

    public LedgerTransaction postTransfer(UUID fromUserId, UserAccountType fromType, UUID toUserId, UserAccountType toType,
                                           BigDecimal amount, String idempotencyKey) {
        UserAccount from = userAccountRepository.requireByUserIdAndType(fromUserId, fromType);
        UserAccount to = userAccountRepository.requireByUserIdAndType(toUserId, toType);
        List<Leg> legs = List.of(
                new Leg(from.getId(), amount, BigDecimal.ZERO),
                new Leg(to.getId(), BigDecimal.ZERO, amount));
        return postLedgerTransaction(idempotencyKey, "TRANSFER", LedgerTransactionType.TRANSFER, amount, "Transfer", legs);
    }

    /** Empty both when the transaction doesn't exist and when it exists but the user wasn't a
     * party to it. A non-participant shouldn't be able to tell those two cases apart. */
    public Optional<LedgerTransaction> findTransactionForUser(UUID transactionId, UUID userId) {
        Optional<LedgerTransaction> transaction = ledgerTransactionDao.findById(transactionId);
        if (transaction.isEmpty() || !ledgerDao.transactionInvolvesUser(transactionId, userId)) {
            return Optional.empty();
        }
        return transaction;
    }

    public List<LedgerEntry> getLegs(UUID transactionId) {
        return ledgerDao.findLegsByTransactionId(transactionId);
    }

    /** Groups every LedgerTransaction posted inside body under one LedgerOperation, and wraps
     * the whole thing in one atomic boundary - any failure rolls back everything body did so far. */
    public <T> T postLedgerOperation(String operationType, UUID referenceId, Function<UUID, T> body) {
        return transactionTemplate.execute(status -> {
            UUID operationId = UUID.randomUUID();
            ledgerOperationDao.insertOperation(new LedgerOperation(operationId, operationType, referenceId, Instant.now()));
            return body.apply(operationId);
        });
    }

    /** Shared by every domain's money-moving service, not just this one. */
    public LedgerTransaction postLedgerTransaction(String idempotencyKey, String requestType, LedgerTransactionType type,
                                                    BigDecimal amount, String description, List<Leg> legs) {
        return postLedgerTransaction(idempotencyKey, requestType, type, amount, description, legs, null);
    }

    public LedgerTransaction postLedgerTransaction(String idempotencyKey, String requestType, LedgerTransactionType type,
                                                    BigDecimal amount, String description, List<Leg> legs,
                                                    UUID operationId) {
        String requestHash = hashRequest(requestType, legs);
        UUID idempotencyId = UUID.randomUUID();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();

        LedgerTransaction result = transactionTemplate.execute(status -> {
            Optional<IdempotencyRecord> existing = idempotencyDao.claim(idempotencyId, idempotencyKey, requestType, requestHash);
            if (existing.isPresent()) {
                return resolveExistingIdempotencyRecord(existing.get(), requestHash, idempotencyKey, failure);
            }

            Map<UUID, LedgerDao.AccountBalance> balances = lockAccountsForLegs(legs);
            if (anyAccountHasInsufficientBalance(legs, balances)) {
                idempotencyDao.markFailed(idempotencyId, "Insufficient balance");
                failure.set(new InsufficientBalanceException("Insufficient balance for one or more accounts"));
                return null;
            }

            LedgerTransaction transaction = insertTransaction(amount, description, type, operationId);
            insertLegs(transaction, legs);
            applyBalanceChanges(legs);
            idempotencyDao.markSucceeded(idempotencyId, transaction.id());
            return transaction;
        });

        if (failure.get() != null) {
            throw failure.get();
        }
        return result;
    }

    private LedgerTransaction resolveExistingIdempotencyRecord(IdempotencyRecord record, String requestHash,
                                                                 String idempotencyKey, AtomicReference<RuntimeException> failure) {
        if (!record.requestHash().equals(requestHash)) {
            failure.set(new IdempotencyKeyConflictException("Idempotency key reused with a different request"));
            return null;
        }
        return switch (record.status()) {
            case SUCCEEDED -> ledgerTransactionDao.findById(record.transactionId()).orElseThrow();
            case IN_PROGRESS, FAILED -> {
                failure.set(new IdempotencyKeyConflictException("Idempotency key already used: " + idempotencyKey));
                yield null;
            }
        };
    }

    private Map<UUID, LedgerDao.AccountBalance> lockAccountsForLegs(List<Leg> legs) {
        List<UUID> accountIds = legs.stream().map(Leg::accountId).toList();
        Map<UUID, LedgerDao.AccountBalance> balances = ledgerDao.lockAccounts(accountIds);

        List<UUID> missingAccountIds = accountIds.stream().distinct()
                .filter(id -> !balances.containsKey(id))
                .toList();
        if (!missingAccountIds.isEmpty()) {
            throw new AccountNotFoundException("No account found for id(s): " + missingAccountIds);
        }
        return balances;
    }

    private boolean anyAccountHasInsufficientBalance(List<Leg> legs, Map<UUID, LedgerDao.AccountBalance> balances) {
        return legs.stream().anyMatch(leg -> {
            LedgerDao.AccountBalance account = balances.get(leg.accountId());
            return leg.debitAmount().signum() > 0 && !account.unlimited()
                    && account.balance().compareTo(leg.debitAmount()) < 0;
        });
    }

    private LedgerTransaction insertTransaction(BigDecimal amount, String description, LedgerTransactionType type, UUID operationId) {
        LedgerTransaction transaction = new LedgerTransaction(UUID.randomUUID(), amount, description, type, operationId, Instant.now());
        ledgerTransactionDao.insertTransaction(transaction);
        return transaction;
    }

    private void insertLegs(LedgerTransaction transaction, List<Leg> legs) {
        List<LedgerEntry> entries = legs.stream()
                .map(leg -> new LedgerEntry(UUID.randomUUID(), leg.accountId(), leg.debitAmount(),
                        leg.creditAmount(), transaction.id(), transaction.createdAt()))
                .toList();
        ledgerDao.insertEntries(entries);
    }

    private void applyBalanceChanges(List<Leg> legs) {
        Map<UUID, BigDecimal> netChanges = legs.stream()
                .collect(Collectors.toMap(Leg::accountId,
                        leg -> leg.creditAmount().subtract(leg.debitAmount()), BigDecimal::add));
        ledgerDao.adjustBalances(netChanges);
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
