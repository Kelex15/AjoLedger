package com.ajoledger.backend.circle;

import com.ajoledger.backend.circle.entity.Circle;
import com.ajoledger.backend.circle.entity.CircleMemberDebitAuthorization;
import com.ajoledger.backend.circle.entity.CirclePayout;
import com.ajoledger.backend.circle.repository.CircleMemberDebitAuthorizationRepository;
import com.ajoledger.backend.circle.repository.CircleMembershipRepository;
import com.ajoledger.backend.circle.repository.CirclePayoutRepository;
import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.account.entity.UserAccount;
import com.ajoledger.backend.account.repository.UserAccountRepository;
import com.ajoledger.backend.circle.policy.CircleSizingPolicy;
import com.ajoledger.backend.ledger.dao.LedgerDao;
import com.ajoledger.backend.ledger.model.LedgerTransaction;
import com.ajoledger.backend.ledger.model.Leg;
import com.ajoledger.backend.ledger.LedgerService;
import com.ajoledger.backend.ledger.LedgerTransactionType;
import com.ajoledger.backend.ledger.exception.InsufficientBalanceException;
import com.ajoledger.backend.ledger.exception.LienWithdrawalBlockedException;
import com.ajoledger.backend.ledger.idempotency.IdempotencyKeys;
import com.ajoledger.backend.system.Constants;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** All ledger-posting for circle money movement: contributions, payouts, liens, and the
 * default waterfall. Doesn't know about membership state. Callers pass in whatever
 * account ids and amounts the posting needs. */
@Service
@RequiredArgsConstructor
public class CircleTransactionsService {

    private static final Logger log = LoggerFactory.getLogger(CircleTransactionsService.class);

    // Payout split: these can be tuned, they just need to sum to <= 1.
    private static final double PAYOUT_LIEN_CAP_RATE = 0.30;
    private static final double PAYOUT_BUFFER_FEE_RATE = 0.02;
    // Placeholder, not tuned: caps how much of the shared buffer a single default can draw.
    private static final double BUFFER_DRAW_CAP_RATE = 0.30;
    // Placeholder, not tuned: buffer's cut of a completing circle's leftover pot, before the
    // remainder is split among members who withdrew before ever reaching their own payout.
    static final double WITHDRAWAL_REFUND_BUFFER_FEE_RATE = 0.02;

    private final LedgerService ledgerService;
    private final LedgerDao ledgerDao;
    private final UserAccountRepository userAccountRepository;
    private final CirclePayoutRepository circlePayoutRepository;
    private final CircleMembershipRepository circleMembershipRepository;
    private final CircleMemberDebitAuthorizationRepository circleMemberDebitAuthorizationRepository;
    private final Map<String, CircleSizingPolicy> sizingPoliciesByType;

    private CircleSizingPolicy resolveSizingPolicy(Circle circle) {
        return sizingPoliciesByType.get(circle.getSizingPolicyType().name());
    }

    private static BigDecimal roundAmount(BigDecimal amount) {
        return amount.setScale(Currency.USD.getDecimalScale(), RoundingMode.HALF_UP);
    }

    public LedgerTransaction postContribution(UUID userId, UUID circleId, UUID circlePotAccountId,
                                         BigDecimal amount, String idempotencyKey) {
        UserAccount savings = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.SAVINGS);
        List<Leg> legs = List.of(
                new Leg(savings.getId(), amount, BigDecimal.ZERO),
                new Leg(circlePotAccountId, BigDecimal.ZERO, amount));
        return ledgerService.postLedgerTransaction(idempotencyKey, "CONTRIBUTION", LedgerTransactionType.CONTRIBUTION,
                amount, savings.getCurrency(), "Contribution to circle " + circleId, legs);
    }

    /** Exact inverse of postContribution's legs. */
    public LedgerTransaction postContributionRefund(UUID userId, UUID circleId, UUID circlePotAccountId,
                                               BigDecimal amount, String idempotencyKey) {
        return postContributionRefund(userId, circleId, circlePotAccountId, amount, idempotencyKey, null);
    }

    /** Same as above, but grouped with other legs under one LedgerOperation. */
    private LedgerTransaction postContributionRefund(UUID userId, UUID circleId, UUID circlePotAccountId,
                                               BigDecimal amount, String idempotencyKey, UUID operationId) {
        UserAccount savings = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.SAVINGS);
        List<Leg> legs = List.of(
                new Leg(circlePotAccountId, amount, BigDecimal.ZERO),
                new Leg(savings.getId(), BigDecimal.ZERO, amount));
        return ledgerService.postLedgerTransaction(idempotencyKey, "CONTRIBUTION_REFUND",
                LedgerTransactionType.CONTRIBUTION_REFUND, amount, savings.getCurrency(),
                "Contribution refund for circle " + circleId, legs, operationId);
    }

    public LedgerTransaction postPayout(UUID circlePotAccountId, UUID circlePayoutId, UUID recipientUserId,
                                   BigDecimal grossAmount, BigDecimal groupLoss, String idempotencyKey) {
        UserAccount savings = userAccountRepository.requireByUserIdAndType(recipientUserId, UserAccountType.SAVINGS);
        UserAccount lien = userAccountRepository.requireByUserIdAndType(recipientUserId, UserAccountType.LIEN);
        UserAccount buffer = userAccountRepository.requireByUserIdAndType(
                Constants.Account.SYSTEM_USER_ID, UserAccountType.SYSTEM_BUFFER);

        BigDecimal lienShare = roundAmount(
                TrustCalculations.calculateCappedLienShare(groupLoss, ledgerDao.getBalance(lien.getId()), grossAmount, PAYOUT_LIEN_CAP_RATE));
        BigDecimal bufferFee = roundAmount(grossAmount.multiply(BigDecimal.valueOf(PAYOUT_BUFFER_FEE_RATE)));
        BigDecimal net = grossAmount.subtract(lienShare).subtract(bufferFee);

        return ledgerService.postLedgerOperation("PAYOUT", circlePayoutId, operationId -> {
            CirclePayout payout = circlePayoutRepository.findById(circlePayoutId)
                    .orElseThrow(() -> new IllegalStateException("CirclePayout not found: " + circlePayoutId));
            payout.setOperationId(operationId);
            circlePayoutRepository.saveAndFlush(payout);

            LedgerTransaction main = ledgerService.postLedgerTransaction(idempotencyKey, "PAYOUT", LedgerTransactionType.PAYOUT,
                    net, savings.getCurrency(), "Payout for circle payout " + circlePayoutId,
                    List.of(new Leg(circlePotAccountId, net, BigDecimal.ZERO), new Leg(savings.getId(), BigDecimal.ZERO, net)),
                    operationId);

            ledgerService.postLedgerTransaction(IdempotencyKeys.of(idempotencyKey, "buffer-fee"), "PAYOUT_BUFFER_FEE",
                    LedgerTransactionType.PAYOUT_BUFFER_FEE, bufferFee, buffer.getCurrency(),
                    "Buffer fee for circle payout " + circlePayoutId,
                    List.of(new Leg(circlePotAccountId, bufferFee, BigDecimal.ZERO), new Leg(buffer.getId(), BigDecimal.ZERO, bufferFee)),
                    operationId);

            if (lienShare.signum() > 0) {
                ledgerService.postLedgerTransaction(IdempotencyKeys.of(idempotencyKey, "lien-skim"), "PAYOUT_LIEN_SKIM",
                        LedgerTransactionType.PAYOUT_LIEN_SKIM, lienShare, lien.getCurrency(),
                        "Lien skim for circle payout " + circlePayoutId,
                        List.of(new Leg(circlePotAccountId, lienShare, BigDecimal.ZERO), new Leg(lien.getId(), BigDecimal.ZERO, lienShare)),
                        operationId);
            }

            return main;
        });
    }

    public LedgerTransaction postLienHold(UUID userId, BigDecimal amount, String idempotencyKey) {
        UserAccount savings = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.SAVINGS);
        UserAccount lien = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.LIEN);
        List<Leg> legs = List.of(
                new Leg(savings.getId(), amount, BigDecimal.ZERO),
                new Leg(lien.getId(), BigDecimal.ZERO, amount));
        return ledgerService.postLedgerTransaction(idempotencyKey, "LIEN_HOLD", LedgerTransactionType.LIEN_HOLD,
                amount, lien.getCurrency(), "Lien top-up", legs);
    }

    public LedgerTransaction postLienWithdrawal(UUID userId, BigDecimal amount, String idempotencyKey) {
        UserAccount lien = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.LIEN);
        BigDecimal lienBalance = ledgerDao.getBalance(lien.getId());
        if (lienBalance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient LIEN balance for withdrawal");
        }
        if (lienBalance.subtract(amount).compareTo(computeLienWithdrawalFloor(userId)) < 0) {
            throw new LienWithdrawalBlockedException("Withdrawal would drop LIEN below the required floor");
        }
        UserAccount savings = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.SAVINGS);
        List<Leg> legs = List.of(
                new Leg(lien.getId(), amount, BigDecimal.ZERO),
                new Leg(savings.getId(), BigDecimal.ZERO, amount));
        return ledgerService.postLedgerTransaction(idempotencyKey, "LIEN_WITHDRAWAL", LedgerTransactionType.LIEN_WITHDRAWAL,
                amount, lien.getCurrency(), "Lien withdrawal", legs);
    }

    /** Zero if the member has no active circle, or their circle hasn't locked yet, so there's
     * no position-justified commitment to protect. Otherwise their share of the group's
     * remaining exposure for the rest of that circle. */
    public BigDecimal computeLienWithdrawalFloor(UUID userId) {
        return circleMembershipRepository.findByUser_IdAndStatus(userId, MembershipStatus.ACTIVE)
                .filter(membership -> membership.getCircle().getStatus() == CircleStatus.ACTIVE)
                .map(membership -> {
                    Circle circle = membership.getCircle();
                    int totalRounds = resolveSizingPolicy(circle).getTotalRounds(circle);
                    return TrustCalculations.calculateGroupLoss(circle.getContributionAmount(), totalRounds,
                            circle.getCurrentRound() - 1);
                })
                .orElse(BigDecimal.ZERO);
    }

    /** Either groupLoss or the ceiling can be the binding constraint, so both shrink together. */
    private record Seizure(BigDecimal amount, BigDecimal remainingGroupLoss, BigDecimal remainingCeilingBudget) {
    }

    private Seizure seize(UserAccount account, BigDecimal remainingGroupLoss, BigDecimal remainingCeilingBudget) {
        BigDecimal amount = ledgerDao.getBalance(account.getId()).min(remainingGroupLoss).min(remainingCeilingBudget);
        return new Seizure(amount, remainingGroupLoss.subtract(amount), remainingCeilingBudget.subtract(amount));
    }

    private void postSeizureLegIfPositive(List<LedgerTransaction> transactions, UserAccount account, BigDecimal amount,
                                           LedgerTransactionType type, String idempotencySuffix, String description,
                                           UUID circleReserveAccountId, String idempotencyKey, UUID operationId) {
        if (amount.signum() <= 0) {
            return;
        }
        List<Leg> legs = List.of(
                new Leg(account.getId(), amount, BigDecimal.ZERO),
                new Leg(circleReserveAccountId, BigDecimal.ZERO, amount));
        transactions.add(ledgerService.postLedgerTransaction(IdempotencyKeys.of(idempotencyKey, idempotencySuffix), type.name(), type,
                amount, account.getCurrency(), description, legs, operationId));
    }

    /** Default waterfall (design/v3.md §5): LIEN, then SAVINGS, then CHECKING, then the buffer.
     *  LIEN first because a DEFAULTED membership drops its withdrawal floor to zero, so collateral
     *  left unseized here would be withdrawable immediately afterwards. */
    public List<LedgerTransaction> postDefaultWaterfall(UUID userId, UUID circleMembershipId, UUID circleReserveAccountId,
                                                    BigDecimal groupLoss, String idempotencyKey) {
        UserAccount savings = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.SAVINGS);
        UserAccount lien = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.LIEN);
        UserAccount checking = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.CHECKING);
        UserAccount buffer = userAccountRepository.requireByUserIdAndType(
                Constants.Account.SYSTEM_USER_ID, UserAccountType.SYSTEM_BUFFER);
        CircleMemberDebitAuthorization authorization = circleMemberDebitAuthorizationRepository
                .findByCircleMembership_Id(circleMembershipId)
                .orElseGet(() -> CircleMemberDebitAuthorization.builder()
                        .circleMembership(circleMembershipRepository.getReferenceById(circleMembershipId))
                        .remainingCeiling(groupLoss)
                        .build());

        Seizure lienSeizure = seize(lien, groupLoss, authorization.getRemainingCeiling());
        Seizure savingsSeizure = seize(savings, lienSeizure.remainingGroupLoss(), lienSeizure.remainingCeilingBudget());
        Seizure checkingSeizure = seize(checking, savingsSeizure.remainingGroupLoss(), savingsSeizure.remainingCeilingBudget());
        BigDecimal remainingLoss = checkingSeizure.remainingGroupLoss();
        BigDecimal totalSeized = lienSeizure.amount().add(savingsSeizure.amount()).add(checkingSeizure.amount());

        return ledgerService.postLedgerOperation("DEFAULT_WATERFALL", circleMembershipId, operationId -> {
            List<LedgerTransaction> transactions = new ArrayList<>();

            postSeizureLegIfPositive(transactions, lien, lienSeizure.amount(), LedgerTransactionType.LIEN_SEIZURE,
                    "lien-seizure", "Lien seizure for membership " + circleMembershipId,
                    circleReserveAccountId, idempotencyKey, operationId);
            postSeizureLegIfPositive(transactions, savings, savingsSeizure.amount(), LedgerTransactionType.SAVINGS_SEIZURE,
                    "savings-seizure", "Savings seizure for membership " + circleMembershipId,
                    circleReserveAccountId, idempotencyKey, operationId);
            postSeizureLegIfPositive(transactions, checking, checkingSeizure.amount(), LedgerTransactionType.CHECKING_SEIZURE,
                    "checking-seizure", "Checking seizure for membership " + circleMembershipId,
                    circleReserveAccountId, idempotencyKey, operationId);

            if (totalSeized.signum() > 0) {
                authorization.setRemainingCeiling(authorization.getRemainingCeiling().subtract(totalSeized));
                circleMemberDebitAuthorizationRepository.save(authorization);
            }

            if (remainingLoss.signum() > 0) {
                BigDecimal bufferPayout = TrustCalculations.calculateCappedAmount(remainingLoss, ledgerDao.getBalance(buffer.getId()),
                        BUFFER_DRAW_CAP_RATE);
                if (bufferPayout.signum() > 0) {
                    List<Leg> bufferLegs = List.of(
                            new Leg(buffer.getId(), bufferPayout, BigDecimal.ZERO),
                            new Leg(circleReserveAccountId, BigDecimal.ZERO, bufferPayout));
                    transactions.add(ledgerService.postLedgerTransaction(IdempotencyKeys.of(idempotencyKey, "buffer-payout"), "BUFFER_PAYOUT_TO_COVER_DEFAULT",
                            LedgerTransactionType.BUFFER_PAYOUT_TO_COVER_DEFAULT, bufferPayout, buffer.getCurrency(),
                            "Buffer payout covering default for membership " + circleMembershipId, bufferLegs, operationId));
                }
                flagUnrecoveredShortfall(circleMembershipId, remainingLoss.subtract(bufferPayout));
            }

            return transactions;
        });
    }

    /** Nothing further to recover here. An unrecovered shortfall just means the circle's own
     * future rounds pay out proportionally less (design/v3.md §5), not a new transfer to post. */
    private void flagUnrecoveredShortfall(UUID circleMembershipId, BigDecimal unrecoveredShortfall) {
        if (unrecoveredShortfall.signum() > 0) {
            log.warn("Circle default shortfall not fully covered for membership {}: ${} unrecovered after lien + buffer",
                    circleMembershipId, unrecoveredShortfall);
        }
    }

    /** Releases part of a circle's reserve into its pot, e.g. to top up a round following a default. */
    public LedgerTransaction postReserveRelease(UUID reserveAccountId, UUID potAccountId, BigDecimal amount,
                                                 Currency currency, int roundNumber, String idempotencyKey) {
        List<Leg> legs = List.of(
                new Leg(reserveAccountId, amount, BigDecimal.ZERO),
                new Leg(potAccountId, BigDecimal.ZERO, amount));
        return ledgerService.postLedgerTransaction(idempotencyKey, "CIRCLE_RESERVE_DISBURSEMENT",
                LedgerTransactionType.CIRCLE_RESERVE_DISBURSEMENT, amount, currency,
                "Reserve release into pot for round " + roundNumber, legs);
    }

    public record WithdrawnMemberShare(UUID userId, UUID membershipId, int roundsContributed) {
    }

    /** Leftover reserve/pot money at completion belongs to members who withdrew before their
     * own payout, not anyone already paid. It's split weighted by rounds contributed, net of a buffer fee. */
    public List<LedgerTransaction> postCircleCompletionRefunds(UUID circleId, UUID reserveAccountId, UUID potAccountId,
                                                                 Currency currency, List<WithdrawnMemberShare> withdrawnShares,
                                                                 String idempotencyKey) {
        int totalRoundsContributed = withdrawnShares.stream().mapToInt(WithdrawnMemberShare::roundsContributed).sum();
        if (totalRoundsContributed <= 0) {
            return List.of();
        }

        return ledgerService.postLedgerOperation("CIRCLE_COMPLETION_REFUND", circleId, operationId -> {
            List<LedgerTransaction> transactions = new ArrayList<>();

            BigDecimal reserveBalance = ledgerDao.getBalance(reserveAccountId);
            if (reserveBalance.signum() > 0) {
                List<Leg> reserveLegs = List.of(
                        new Leg(reserveAccountId, reserveBalance, BigDecimal.ZERO),
                        new Leg(potAccountId, BigDecimal.ZERO, reserveBalance));
                transactions.add(ledgerService.postLedgerTransaction(IdempotencyKeys.of(idempotencyKey, "reserve-sweep"), "CIRCLE_RESERVE_DISBURSEMENT",
                        LedgerTransactionType.CIRCLE_RESERVE_DISBURSEMENT, reserveBalance, currency,
                        "Reserve swept into pot for circle " + circleId + " completion", reserveLegs, operationId));
            }

            BigDecimal potBalance = ledgerDao.getBalance(potAccountId);
            if (potBalance.signum() <= 0) {
                return transactions;
            }

            BigDecimal bufferFee = roundAmount(potBalance.multiply(BigDecimal.valueOf(WITHDRAWAL_REFUND_BUFFER_FEE_RATE)));
            if (bufferFee.signum() > 0) {
                UserAccount buffer = userAccountRepository.requireByUserIdAndType(Constants.Account.SYSTEM_USER_ID, UserAccountType.SYSTEM_BUFFER);
                List<Leg> feeLegs = List.of(
                        new Leg(potAccountId, bufferFee, BigDecimal.ZERO),
                        new Leg(buffer.getId(), BigDecimal.ZERO, bufferFee));
                transactions.add(ledgerService.postLedgerTransaction(IdempotencyKeys.of(idempotencyKey, "buffer-fee"), "PAYOUT_BUFFER_FEE",
                        LedgerTransactionType.PAYOUT_BUFFER_FEE, bufferFee, currency,
                        "Withdrawal refund buffer fee for circle " + circleId, feeLegs, operationId));
            }

            BigDecimal refundable = potBalance.subtract(bufferFee);
            if (refundable.signum() <= 0) {
                return transactions;
            }

            BigDecimal perRoundShare = refundable.divide(BigDecimal.valueOf(totalRoundsContributed), 10, RoundingMode.HALF_UP);
            for (WithdrawnMemberShare share : withdrawnShares) {
                if (share.roundsContributed() <= 0) {
                    continue;
                }
                BigDecimal amount = roundAmount(perRoundShare.multiply(BigDecimal.valueOf(share.roundsContributed())));
                if (amount.signum() > 0) {
                    transactions.add(postContributionRefund(share.userId(), circleId, potAccountId, amount,
                            IdempotencyKeys.of(idempotencyKey, share.membershipId()), operationId));
                }
            }
            return transactions;
        });
    }
}
