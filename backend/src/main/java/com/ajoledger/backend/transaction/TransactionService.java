package com.ajoledger.backend.transaction;

import com.ajoledger.backend.account.UserAccount;
import com.ajoledger.backend.account.UserAccountRepository;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.account.exception.AccountNotFoundException;
import com.ajoledger.backend.circle.CircleMembershipRepository;
import com.ajoledger.backend.circle.CirclePayout;
import com.ajoledger.backend.circle.CirclePayoutRepository;
import com.ajoledger.backend.circle.MembershipStatus;
import com.ajoledger.backend.fundingsource.FundingSourceType;
import com.ajoledger.backend.ledger.LedgerDao;
import com.ajoledger.backend.ledger.LedgerEntry;
import com.ajoledger.backend.ledger.exception.InsufficientBalanceException;
import com.ajoledger.backend.transaction.exception.IdempotencyKeyConflictException;
import com.ajoledger.backend.transaction.exception.LienWithdrawalBlockedException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
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

    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final BigDecimal BUFFER_FEE_RATE = new BigDecimal("0.01");

    private final TransactionTemplate transactionTemplate;
    private final LedgerDao ledgerDao;
    private final TransactionDao transactionDao;
    private final IdempotencyDao idempotencyDao;
    private final UserAccountRepository userAccountRepository;
    private final CircleMembershipRepository circleMembershipRepository;
    private final CirclePayoutRepository circlePayoutRepository;

    // ---- public API ----

    public Transaction postDeposit(UUID userId, UserAccountType targetType, FundingSourceType source,
                                    BigDecimal amount, String idempotencyKey) {
        UserAccount clearing = requireUserAccount(SYSTEM_USER_ID, clearingAccountType(source));
        UserAccount target = requireUserAccount(userId, targetType);
        List<Leg> legs = List.of(
                new Leg(clearing.getId(), amount, BigDecimal.ZERO),
                new Leg(target.getId(), BigDecimal.ZERO, amount));
        return postLedgerTransaction(idempotencyKey, "DEPOSIT", TransactionType.DEPOSIT, amount,
                "Deposit via " + source, legs);
    }

    public Transaction postWithdrawal(UUID userId, UserAccountType sourceType, FundingSourceType destination,
                                       BigDecimal amount, String idempotencyKey) {
        UserAccount source = requireUserAccount(userId, sourceType);
        UserAccount clearing = requireUserAccount(SYSTEM_USER_ID, clearingAccountType(destination));
        List<Leg> legs = List.of(
                new Leg(source.getId(), amount, BigDecimal.ZERO),
                new Leg(clearing.getId(), BigDecimal.ZERO, amount));
        return postLedgerTransaction(idempotencyKey, "WITHDRAWAL", TransactionType.WITHDRAWAL, amount,
                "Withdrawal to " + destination, legs);
    }

    public Transaction postTransfer(UUID fromUserId, UserAccountType fromType, UUID toUserId, UserAccountType toType,
                                     BigDecimal amount, String idempotencyKey) {
        UserAccount from = requireUserAccount(fromUserId, fromType);
        UserAccount to = requireUserAccount(toUserId, toType);
        List<Leg> legs = List.of(
                new Leg(from.getId(), amount, BigDecimal.ZERO),
                new Leg(to.getId(), BigDecimal.ZERO, amount));
        return postLedgerTransaction(idempotencyKey, "TRANSFER", TransactionType.TRANSFER, amount, "Transfer", legs);
    }

    public Transaction postContribution(UUID userId, UUID circleId, UUID circlePotAccountId,
                                         BigDecimal amount, String idempotencyKey) {
        UserAccount savings = requireUserAccount(userId, UserAccountType.SAVINGS);
        UserAccount buffer = requireUserAccount(SYSTEM_USER_ID, UserAccountType.SYSTEM_BUFFER);
        BigDecimal fee = amount.multiply(BUFFER_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netToPot = amount.subtract(fee);
        List<Leg> legs = List.of(
                new Leg(savings.getId(), amount, BigDecimal.ZERO),
                new Leg(circlePotAccountId, BigDecimal.ZERO, netToPot),
                new Leg(buffer.getId(), BigDecimal.ZERO, fee));
        return postLedgerTransaction(idempotencyKey, "CONTRIBUTION", TransactionType.CONTRIBUTION, amount,
                "Contribution to circle " + circleId, legs);
    }

    public Transaction postPayout(UUID circlePotAccountId, UUID circlePayoutId, UUID recipientUserId,
                                   BigDecimal grossAmount, BigDecimal withheldAmount, String idempotencyKey) {
        UserAccount savings = requireUserAccount(recipientUserId, UserAccountType.SAVINGS);
        UserAccount lien = requireUserAccount(recipientUserId, UserAccountType.LIEN);
        BigDecimal net = grossAmount.subtract(withheldAmount);
        List<Leg> legs = List.of(
                new Leg(circlePotAccountId, grossAmount, BigDecimal.ZERO),
                new Leg(savings.getId(), BigDecimal.ZERO, net),
                new Leg(lien.getId(), BigDecimal.ZERO, withheldAmount));
        return postLedgerTransaction(idempotencyKey, "PAYOUT", TransactionType.PAYOUT, grossAmount,
                "Payout for circle payout " + circlePayoutId, legs,
                transaction -> {
                    CirclePayout payout = circlePayoutRepository.findById(circlePayoutId)
                            .orElseThrow(() -> new IllegalStateException("CirclePayout not found: " + circlePayoutId));
                    payout.setTransactionId(transaction.id());
                    circlePayoutRepository.saveAndFlush(payout);
                });
    }

    public Transaction postLienHold(UUID userId, BigDecimal amount, String idempotencyKey) {
        UserAccount savings = requireUserAccount(userId, UserAccountType.SAVINGS);
        UserAccount lien = requireUserAccount(userId, UserAccountType.LIEN);
        List<Leg> legs = List.of(
                new Leg(savings.getId(), amount, BigDecimal.ZERO),
                new Leg(lien.getId(), BigDecimal.ZERO, amount));
        return postLedgerTransaction(idempotencyKey, "LIEN_HOLD", TransactionType.LIEN_HOLD, amount,
                "Lien top-up", legs);
    }

    public Transaction postLienWithdrawal(UUID userId, BigDecimal amount, String idempotencyKey) {
        boolean hasActiveMembership = circleMembershipRepository.existsByUser_IdAndStatus(userId, MembershipStatus.ACTIVE);
        if (hasActiveMembership) {
            throw new LienWithdrawalBlockedException("Cannot withdraw lien while an active circle membership exists");
        }
        UserAccount lien = requireUserAccount(userId, UserAccountType.LIEN);
        UserAccount savings = requireUserAccount(userId, UserAccountType.SAVINGS);
        List<Leg> legs = List.of(
                new Leg(lien.getId(), amount, BigDecimal.ZERO),
                new Leg(savings.getId(), BigDecimal.ZERO, amount));
        return postLedgerTransaction(idempotencyKey, "LIEN_WITHDRAWAL", TransactionType.LIEN_WITHDRAWAL, amount,
                "Lien withdrawal", legs);
    }

    /**
     * Default waterfall (design/v1.md Section 5, 3 steps): seize LIEN first, then the shared
     * buffer covers the remainder. If the buffer also can't cover it, GROUP_SHORTFALL_ABSORPTION's
     * crediting logic is a follow-on circle-settlement design question, not implemented here.
     */
    public List<Transaction> postDefaultWaterfall(UUID userId, UUID circleMembershipId, BigDecimal groupLoss,
                                                    String idempotencyKey) {
        UserAccount lien = requireUserAccount(userId, UserAccountType.LIEN);
        UserAccount buffer = requireUserAccount(SYSTEM_USER_ID, UserAccountType.SYSTEM_BUFFER);

        BigDecimal lienBalance = lien.getBalance();
        BigDecimal seizedFromLien = lienBalance.min(groupLoss);
        BigDecimal remainingLoss = groupLoss.subtract(seizedFromLien);

        List<Transaction> transactions = new java.util.ArrayList<>();

        if (seizedFromLien.signum() > 0) {
            List<Leg> seizureLegs = List.of(
                    new Leg(lien.getId(), seizedFromLien, BigDecimal.ZERO),
                    new Leg(buffer.getId(), BigDecimal.ZERO, seizedFromLien));
            transactions.add(postLedgerTransaction(idempotencyKey + ":lien-seizure", "LIEN_SEIZURE",
                    TransactionType.LIEN_SEIZURE, seizedFromLien,
                    "Lien seizure for membership " + circleMembershipId, seizureLegs));
        }

        if (remainingLoss.signum() > 0) {
            if (buffer.getBalance().compareTo(remainingLoss) < 0) {
                throw new InsufficientBalanceException(
                        "Shared insurance buffer cannot cover remaining default loss for membership " + circleMembershipId);
            }
            UserAccount recipientSavings = requireUserAccount(userId, UserAccountType.SAVINGS);
            List<Leg> bufferLegs = List.of(
                    new Leg(buffer.getId(), remainingLoss, BigDecimal.ZERO),
                    new Leg(recipientSavings.getId(), BigDecimal.ZERO, remainingLoss));
            transactions.add(postLedgerTransaction(idempotencyKey + ":buffer-payout", "BUFFER_PAYOUT_TO_COVER_DEFAULT",
                    TransactionType.BUFFER_PAYOUT_TO_COVER_DEFAULT, remainingLoss,
                    "Buffer payout covering default for membership " + circleMembershipId, bufferLegs));
        }

        return transactions;
    }

    // ---- shared helper ----

    private record Leg(UUID accountId, BigDecimal debitAmount, BigDecimal creditAmount) {
    }

    private Transaction postLedgerTransaction(String idempotencyKey, String requestType, TransactionType type,
                                               BigDecimal amount, String description, List<Leg> legs) {
        return postLedgerTransaction(idempotencyKey, requestType, type, amount, description, legs, t -> {
        });
    }

    private Transaction postLedgerTransaction(String idempotencyKey, String requestType, TransactionType type,
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
            Map<UUID, BigDecimal> balances = ledgerDao.lockAccounts(accountIds);

            boolean insufficient = legs.stream().anyMatch(leg ->
                    leg.debitAmount().signum() > 0 && balances.get(leg.accountId()).compareTo(leg.debitAmount()) < 0);

            if (insufficient) {
                idempotencyDao.markFailed(idempotencyId, "Insufficient balance");
                failure.set(new InsufficientBalanceException("Insufficient balance for one or more accounts"));
                return null;
            }

            Transaction transaction = new Transaction(UUID.randomUUID(), amount, description, type, LocalDateTime.now());
            transactionDao.insertTransaction(transaction);

            List<LedgerEntry> entries = legs.stream()
                    .map(leg -> new LedgerEntry(UUID.randomUUID(), leg.accountId(), leg.debitAmount(),
                            leg.creditAmount(), transaction.id(), LocalDateTime.now()))
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

    private UserAccountType clearingAccountType(FundingSourceType source) {
        return switch (source) {
            case STRIPE -> UserAccountType.CLEARING_STRIPE;
            case PAYPAL -> UserAccountType.CLEARING_PAYPAL;
        };
    }

    private UserAccount requireUserAccount(UUID userId, UserAccountType type) {
        return userAccountRepository.findByUserIdAndUserAccountType(userId, type)
                .orElseThrow(() -> new AccountNotFoundException("No " + type + " account for user " + userId));
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
