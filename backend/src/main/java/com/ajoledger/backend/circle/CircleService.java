package com.ajoledger.backend.circle;

import com.ajoledger.backend.account.CircleAccount;
import com.ajoledger.backend.account.CircleAccountRepository;
import com.ajoledger.backend.account.UserAccount;
import com.ajoledger.backend.account.UserAccountRepository;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.circle.policy.CircleSizingPolicy;
import com.ajoledger.backend.ledger.exception.InsufficientBalanceException;
import com.ajoledger.backend.system.Constants;
import com.ajoledger.backend.transaction.Leg;
import com.ajoledger.backend.transaction.Transaction;
import com.ajoledger.backend.transaction.TransactionService;
import com.ajoledger.backend.transaction.TransactionType;
import com.ajoledger.backend.transaction.exception.LienWithdrawalBlockedException;
import com.ajoledger.backend.user.User;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CircleService {

    private static final BigDecimal BUFFER_FEE_RATE = new BigDecimal("0.01");

    private final TransactionService transactionService;
    private final UserAccountRepository userAccountRepository;
    private final CircleRepository circleRepository;
    private final CircleAccountRepository circleAccountRepository;
    private final CircleMembershipRepository circleMembershipRepository;
    private final CirclePayoutRepository circlePayoutRepository;
    private final CircleSizingPolicy circleSizingPolicy;

    public Transaction postContribution(UUID userId, UUID circleId, UUID circlePotAccountId,
                                         BigDecimal amount, String idempotencyKey) {
        UserAccount savings = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.SAVINGS);
        UserAccount buffer = userAccountRepository.requireByUserIdAndType(
                Constants.Account.SYSTEM_USER_ID, UserAccountType.SYSTEM_BUFFER);
        BigDecimal fee = amount.multiply(BUFFER_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netToPot = amount.subtract(fee);
        List<Leg> legs = List.of(
                new Leg(savings.getId(), amount, BigDecimal.ZERO),
                new Leg(circlePotAccountId, BigDecimal.ZERO, netToPot),
                new Leg(buffer.getId(), BigDecimal.ZERO, fee));
        return transactionService.postLedgerTransaction(idempotencyKey, "CONTRIBUTION", TransactionType.CONTRIBUTION,
                amount, "Contribution to circle " + circleId, legs);
    }

    public Transaction postPayout(UUID circlePotAccountId, UUID circlePayoutId, UUID recipientUserId,
                                   BigDecimal grossAmount, BigDecimal withheldAmount, String idempotencyKey) {
        UserAccount savings = userAccountRepository.requireByUserIdAndType(recipientUserId, UserAccountType.SAVINGS);
        UserAccount lien = userAccountRepository.requireByUserIdAndType(recipientUserId, UserAccountType.LIEN);
        BigDecimal net = grossAmount.subtract(withheldAmount);
        List<Leg> legs = List.of(
                new Leg(circlePotAccountId, grossAmount, BigDecimal.ZERO),
                new Leg(savings.getId(), BigDecimal.ZERO, net),
                new Leg(lien.getId(), BigDecimal.ZERO, withheldAmount));
        return transactionService.postLedgerTransaction(idempotencyKey, "PAYOUT", TransactionType.PAYOUT, grossAmount,
                "Payout for circle payout " + circlePayoutId, legs,
                transaction -> {
                    CirclePayout payout = circlePayoutRepository.findById(circlePayoutId)
                            .orElseThrow(() -> new IllegalStateException("CirclePayout not found: " + circlePayoutId));
                    payout.setTransactionId(transaction.id());
                    circlePayoutRepository.saveAndFlush(payout);
                });
    }

    public Transaction postLienHold(UUID userId, BigDecimal amount, String idempotencyKey) {
        UserAccount savings = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.SAVINGS);
        UserAccount lien = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.LIEN);
        List<Leg> legs = List.of(
                new Leg(savings.getId(), amount, BigDecimal.ZERO),
                new Leg(lien.getId(), BigDecimal.ZERO, amount));
        return transactionService.postLedgerTransaction(idempotencyKey, "LIEN_HOLD", TransactionType.LIEN_HOLD,
                amount, "Lien top-up", legs);
    }

    public Transaction postLienWithdrawal(UUID userId, BigDecimal amount, String idempotencyKey) {
        boolean hasActiveMembership = circleMembershipRepository.existsByUser_IdAndStatus(userId, MembershipStatus.ACTIVE);
        if (hasActiveMembership) {
            throw new LienWithdrawalBlockedException("Cannot withdraw lien while an active circle membership exists");
        }
        UserAccount lien = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.LIEN);
        UserAccount savings = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.SAVINGS);
        List<Leg> legs = List.of(
                new Leg(lien.getId(), amount, BigDecimal.ZERO),
                new Leg(savings.getId(), BigDecimal.ZERO, amount));
        return transactionService.postLedgerTransaction(idempotencyKey, "LIEN_WITHDRAWAL", TransactionType.LIEN_WITHDRAWAL,
                amount, "Lien withdrawal", legs);
    }

    /**
     * Default waterfall (design/v1.md Section 5, 3 steps): seize LIEN first, then the shared
     * buffer covers the remainder. If the buffer also can't cover it, GROUP_SHORTFALL_ABSORPTION's
     * crediting logic is a follow-on circle-settlement design question, not implemented here.
     */
    public List<Transaction> postDefaultWaterfall(UUID userId, UUID circleMembershipId, BigDecimal groupLoss,
                                                    String idempotencyKey) {
        UserAccount lien = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.LIEN);
        UserAccount buffer = userAccountRepository.requireByUserIdAndType(
                Constants.Account.SYSTEM_USER_ID, UserAccountType.SYSTEM_BUFFER);

        BigDecimal lienBalance = lien.getBalance();
        BigDecimal seizedFromLien = lienBalance.min(groupLoss);
        BigDecimal remainingLoss = groupLoss.subtract(seizedFromLien);

        List<Transaction> transactions = new ArrayList<>();

        if (seizedFromLien.signum() > 0) {
            List<Leg> seizureLegs = List.of(
                    new Leg(lien.getId(), seizedFromLien, BigDecimal.ZERO),
                    new Leg(buffer.getId(), BigDecimal.ZERO, seizedFromLien));
            transactions.add(transactionService.postLedgerTransaction(idempotencyKey + ":lien-seizure", "LIEN_SEIZURE",
                    TransactionType.LIEN_SEIZURE, seizedFromLien,
                    "Lien seizure for membership " + circleMembershipId, seizureLegs));
        }

        if (remainingLoss.signum() > 0) {
            if (buffer.getBalance().compareTo(remainingLoss) < 0) {
                throw new InsufficientBalanceException(
                        "Shared insurance buffer cannot cover remaining default loss for membership " + circleMembershipId);
            }
            UserAccount recipientSavings = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.SAVINGS);
            List<Leg> bufferLegs = List.of(
                    new Leg(buffer.getId(), remainingLoss, BigDecimal.ZERO),
                    new Leg(recipientSavings.getId(), BigDecimal.ZERO, remainingLoss));
            transactions.add(transactionService.postLedgerTransaction(idempotencyKey + ":buffer-payout", "BUFFER_PAYOUT_TO_COVER_DEFAULT",
                    TransactionType.BUFFER_PAYOUT_TO_COVER_DEFAULT, remainingLoss,
                    "Buffer payout covering default for membership " + circleMembershipId, bufferLegs));
        }

        return transactions;
    }

    // ---- scheduler interface: called once per circle a scheduled job decides is due ----

    /**
     * Orchestrates a round's contribution deadline for one circle: sweep unpaid members' SAVINGS,
     * mark whoever's still unpaid, then resolve the round if it's now fully funded.
     */
    public void processRoundDeadline(UUID circleId) {
        sweepUnpaidContributions(circleId);
        markNonPayers(circleId);
        resolveRoundIfComplete(circleId);
    }

    private void sweepUnpaidContributions(UUID circleId) {
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new IllegalStateException("Circle not found: " + circleId));
        CircleAccount pot = circleAccountRepository.requireByCircleId(circleId);
        List<CircleMembership> unpaid = unpaidActiveMemberships(circle);

        for (CircleMembership membership : unpaid) {
            try {
                postContribution(membership.getUser().getId(), circleId, pot.getId(), circle.getContributionAmount(),
                        circleId + ":" + membership.getId() + ":round-" + circle.getCurrentRound());
                membership.setLastContributedRound(circle.getCurrentRound());
                circleMembershipRepository.save(membership);
            } catch (InsufficientBalanceException e) {
                // left unpaid - markNonPayers handles it next
            }
        }
    }

    private void markNonPayers(UUID circleId) {
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new IllegalStateException("Circle not found: " + circleId));

        for (CircleMembership membership : unpaidActiveMemberships(circle)) {
            User user = membership.getUser();
            boolean alreadyPaidOut = circlePayoutRepository.existsByCircle_IdAndRecipient_Id(circleId, user.getId());

            if (alreadyPaidOut) {
                membership.setStatus(MembershipStatus.DEFAULTED);
                circleMembershipRepository.save(membership);
                int position = membership.getPosition() != null ? membership.getPosition() : 0;
                BigDecimal groupLoss = circleSizingPolicy.calculatePotentialGroupLoss(circle, user, position);
                postDefaultWaterfall(user.getId(), membership.getId(), groupLoss,
                        circleId + ":" + membership.getId() + ":default-round-" + circle.getCurrentRound());
            } else {
                membership.setStatus(MembershipStatus.WITHDRAWN);
                circleMembershipRepository.save(membership);
            }
        }
    }

    private void resolveRoundIfComplete(UUID circleId) {
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new IllegalStateException("Circle not found: " + circleId));
        boolean everyoneCurrent = circleMembershipRepository.findByCircle_IdAndStatus(circleId, MembershipStatus.ACTIVE)
                .stream()
                .allMatch(m -> m.getLastContributedRound() != null && m.getLastContributedRound() >= circle.getCurrentRound());

        if (!everyoneCurrent) {
            return;
        }

        UUID recipientUserId = getCurrentPayoutReceiver(circleId);
        if (recipientUserId == null) {
            return;
        }

        // TODO: build the CirclePayout row, withheld-amount split, and call postPayout - blocked
        // on the same payout-position design this stub is standing in for.

        circle.setCurrentRound(circle.getCurrentRound() + 1);
        circle.setLastRoundAt(LocalDateTime.now());
        circleRepository.save(circle);
    }

    private List<CircleMembership> unpaidActiveMemberships(Circle circle) {
        return circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.ACTIVE).stream()
                .filter(m -> m.getLastContributedRound() == null || m.getLastContributedRound() < circle.getCurrentRound())
                .toList();
    }

    /** Who receives this circle's current round payout. Payout-position assignment isn't designed yet. */
    private UUID getCurrentPayoutReceiver(UUID circleId) {
        return null;
    }
}
