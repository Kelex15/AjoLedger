package com.ajoledger.backend.circle;

import com.ajoledger.backend.account.CircleAccountType;
import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.account.entity.CircleAccount;
import com.ajoledger.backend.account.repository.CircleAccountRepository;
import com.ajoledger.backend.circle.policy.CircleSizingPolicy;
import com.ajoledger.backend.email.EmailSender;
import com.ajoledger.backend.ledger.LedgerDao;
import com.ajoledger.backend.ledger.exception.InsufficientBalanceException;
import com.ajoledger.backend.user.User;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Round-by-round processing for already-locked circles: sweeping unpaid contributions,
 * handling non-payers (withdrawal or default), and resolving/completing rounds as they pay
 * out. Driven by CircleRoundScheduler; never reaches back into membership formation. */
@Service
@RequiredArgsConstructor
public class CircleRoundService {

    private final CircleRepository circleRepository;
    private final CircleMembershipRepository circleMembershipRepository;
    private final CircleAccountRepository circleAccountRepository;
    private final CirclePayoutRepository circlePayoutRepository;
    private final LedgerDao ledgerDao;
    private final Map<String, CircleSizingPolicy> sizingPoliciesByType;
    private final TransactionTemplate transactionTemplate;
    private final EmailSender emailSender;
    private final CircleTransactionsService circleTransactionsService;

    private CircleSizingPolicy resolveSizingPolicy(Circle circle) {
        return sizingPoliciesByType.get(circle.getSizingPolicyType().name());
    }

    private static BigDecimal roundAmount(BigDecimal amount) {
        return amount.setScale(Currency.USD.getDecimalScale(), RoundingMode.HALF_UP);
    }

    /** The scheduler is blind to how "due" is decided. It just gets ids to process. */
    public List<UUID> findCirclesWithRoundsDueToday() {
        Instant now = Instant.now();
        return circleRepository.findByStatusAndLastRoundAtIsNotNull(CircleStatus.ACTIVE).stream()
                .filter(circle -> !circle.getLastRoundAt().plus(Duration.ofDays(circle.getTerm() * 7L)).isAfter(now))
                .map(Circle::getId)
                .toList();
    }

    public void processRoundDeadline(UUID circleId) {
        sweepUnpaidContributions(circleId);
        markNonPayers(circleId);
        resolveRoundIfComplete(circleId);
    }

    private void sweepUnpaidContributions(UUID circleId) {
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new IllegalStateException("Circle not found: " + circleId));
        CircleAccount pot = circleAccountRepository.requireByCircleIdAndType(circleId, CircleAccountType.POT);
        List<UUID> unpaidMembershipIds = unpaidActiveMemberships(circle).stream().map(CircleMembership::getId).toList();

        for (UUID membershipId : unpaidMembershipIds) {
            sweepContribution(circle, pot.getId(), membershipId);
        }
    }

    /** Per membership, not per loop, so a contribution succeeding but the status update failing can't leave someone incorrectly treated as unpaid. */
    private void sweepContribution(Circle circle, UUID potAccountId, UUID membershipId) {
        transactionTemplate.execute(status -> {
            CircleMembership membership = circleMembershipRepository.findById(membershipId).orElseThrow();
            try {
                circleTransactionsService.postContribution(membership.getUser().getId(), circle.getId(), potAccountId,
                        circle.getContributionAmount(), circle.getId() + ":" + membership.getId() + ":round-" + circle.getCurrentRound());
                membership.setLastContributedRound(circle.getCurrentRound());
                circleMembershipRepository.save(membership);
            } catch (InsufficientBalanceException e) {
                // left unpaid. markNonPayers handles it next.
            }
            return null;
        });
    }

    private void markNonPayers(UUID circleId) {
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new IllegalStateException("Circle not found: " + circleId));
        CircleAccount reserve = circleAccountRepository.requireByCircleIdAndType(circleId, CircleAccountType.RESERVE);
        List<UUID> unpaidMembershipIds = unpaidActiveMemberships(circle).stream().map(CircleMembership::getId).toList();

        for (UUID membershipId : unpaidMembershipIds) {
            processNonPayer(circle, reserve.getId(), membershipId);
        }
    }

    /** Per membership, not per loop, so one failure can't roll back another's already-successful processing. */
    private void processNonPayer(Circle circle, UUID reserveAccountId, UUID membershipId) {
        transactionTemplate.execute(status -> {
            // Re-fetched since an earlier withdrawal in this same loop may have shifted this
            // membership's position.
            CircleMembership membership = circleMembershipRepository.findById(membershipId).orElseThrow();
            User user = membership.getUser();
            boolean alreadyPaidOut = circlePayoutRepository.existsByCircle_IdAndRecipient_Id(circle.getId(), user.getId());

            if (alreadyPaidOut) {
                int position = membership.getPosition() != null ? membership.getPosition() : 0;
                BigDecimal groupLoss = resolveSizingPolicy(circle).calculatePotentialGroupLoss(circle, user, position);

                // Waterfall runs first, membership only flips to DEFAULTED once funds are actually
                // seized. If this throws, the membership stays ACTIVE so the next scheduler pass
                // retries instead of leaving a defaulted membership with nothing recovered.
                circleTransactionsService.postDefaultWaterfall(user.getId(), membership.getId(), reserveAccountId, groupLoss,
                        circle.getId() + ":" + membership.getId() + ":default-round-" + circle.getCurrentRound());

                membership.setStatus(MembershipStatus.DEFAULTED);
                circleMembershipRepository.save(membership);
                notifyOfDefault(circle, user, groupLoss);
            } else {
                int withdrawnPosition = membership.getPosition();
                membership.setStatus(MembershipStatus.WITHDRAWN);
                circleMembershipRepository.save(membership);
                compactPositionsAfterWithdrawal(circle, withdrawnPosition);
            }
            return null;
        });
    }

    /** Defaulter gets their own message; every other active member (excludes the defaulter,
     * already flipped to DEFAULTED by the time this runs) gets a group-facing one. */
    private void notifyOfDefault(Circle circle, User defaulter, BigDecimal groupLoss) {
        emailSender.sendDefaultNotificationEmail(defaulter.getEmail(),
                "You defaulted on circle \"" + circle.getName() + "\". Loss incurred: $" + groupLoss);

        String groupMessage = "A member of your circle \"" + circle.getName() + "\" defaulted. Loss incurred: $" + groupLoss;
        for (CircleMembership membership : circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.ACTIVE)) {
            emailSender.sendDefaultNotificationEmail(membership.getUser().getEmail(), groupMessage);
        }
    }

    /** Closes the gap a pre-payout withdrawal leaves, so a future round never stalls looking
     * for a position nobody holds anymore. */
    private void compactPositionsAfterWithdrawal(Circle circle, int withdrawnPosition) {
        circle.setLockedMemberCount(circle.getLockedMemberCount() - 1);
        circleRepository.save(circle);

        for (CircleMembership other : circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.ACTIVE)) {
            if (other.getPosition() > withdrawnPosition) {
                other.setPosition(other.getPosition() - 1);
                circleMembershipRepository.save(other);
            }
        }
    }

    /** Also the entry point once a circle first locks: an activating circle's members have
     * already paid round 1 via joinCircle, so the round can already be complete at lock time. */
    public void resolveRoundIfComplete(UUID circleId) {
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new IllegalStateException("Circle not found: " + circleId));
        List<CircleMembership> activeMembers = circleMembershipRepository.findByCircle_IdAndStatus(circleId, MembershipStatus.ACTIVE);
        boolean everyoneCurrent = activeMembers.stream()
                .allMatch(m -> m.getLastContributedRound() != null && m.getLastContributedRound() >= circle.getCurrentRound());

        if (!everyoneCurrent) {
            return;
        }

        CircleMembership recipientMembership = getCurrentPayoutReceiver(circle);
        if (recipientMembership != null) {
            resolvePayoutRound(circle, recipientMembership);
        }

        // Checked regardless of whether this call paid anyone: a withdrawal can vacate the
        // circle's last remaining position with nobody left to shift into it, so a round with
        // no recipient still needs to be recognized as "everyone left is already paid."
        Set<UUID> paidRecipientIds = new HashSet<>(circlePayoutRepository.findRecipientIdsByCircle_Id(circleId));
        boolean everyonePaid = activeMembers.stream()
                .allMatch(m -> paidRecipientIds.contains(m.getUser().getId()));
        if (everyonePaid) {
            completeCircle(circle, resolveSizingPolicy(circle).getTotalRounds(circle));
        }
    }

    /** One atomic unit: the reserve release, the payout, and advancing the round all belong to this one payout event. */
    private void resolvePayoutRound(Circle circle, CircleMembership recipientMembership) {
        transactionTemplate.execute(status -> {
            UUID circleId = circle.getId();
            User recipient = recipientMembership.getUser();
            releaseReserveShare(circle);
            CircleAccount pot = circleAccountRepository.requireByCircleIdAndType(circleId, CircleAccountType.POT);
            // Pay out what the pot actually holds, not calculatePotSize's theoretical figure —
            // a reserve release (above) can top it up beyond that on a round following a default.
            BigDecimal grossAmount = ledgerDao.getBalance(pot.getId());

            CirclePayout payout = circlePayoutRepository.save(CirclePayout.builder()
                    .circle(circle)
                    .recipient(recipient)
                    .roundNumber(circle.getCurrentRound())
                    .build());

            BigDecimal groupLoss = resolveSizingPolicy(circle)
                    .calculatePotentialGroupLoss(circle, recipient, recipientMembership.getPosition());
            circleTransactionsService.postPayout(pot.getId(), payout.getId(), recipient.getId(), grossAmount, groupLoss,
                    circleId + ":" + recipientMembership.getId() + ":payout-round-" + circle.getCurrentRound());

            circle.setCurrentRound(circle.getCurrentRound() + 1);
            circle.setLastRoundAt(Instant.now());
            circleRepository.save(circle);
            return null;
        });
    }

    /** One atomic unit: the circle and every remaining member finish together, or not at all. */
    private void completeCircle(Circle circle, int totalRounds) {
        transactionTemplate.execute(status -> {
            circle.setStatus(CircleStatus.COMPLETED);
            circle.setCurrentRound(totalRounds);
            circleRepository.save(circle);
            for (CircleMembership membership : circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.ACTIVE)) {
                membership.setStatus(MembershipStatus.COMPLETED);
                circleMembershipRepository.save(membership);
            }
            return null;
        });
    }

    /** Spreads the reserve's current balance evenly across the rounds still ahead, rather than
     * dumping a recovered default into whichever round resolves next. */
    private void releaseReserveShare(Circle circle) {
        UUID circleId = circle.getId();
        CircleAccount reserve = circleAccountRepository.requireByCircleIdAndType(circleId, CircleAccountType.RESERVE);
        BigDecimal reserveBalance = ledgerDao.getBalance(reserve.getId());
        if (reserveBalance.signum() <= 0) {
            return;
        }

        CircleAccount pot = circleAccountRepository.requireByCircleIdAndType(circleId, CircleAccountType.POT);
        int roundsRemaining = resolveSizingPolicy(circle).getTotalRounds(circle) - circle.getCurrentRound() + 1;
        BigDecimal releaseAmount = roundAmount(reserveBalance.divide(BigDecimal.valueOf(roundsRemaining), 10, RoundingMode.HALF_UP));

        circleTransactionsService.postReserveRelease(reserve.getId(), pot.getId(), releaseAmount, reserve.getCurrency(),
                circle.getCurrentRound(), circleId + ":reserve-release-round-" + circle.getCurrentRound());
    }

    private List<CircleMembership> unpaidActiveMemberships(Circle circle) {
        return circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.ACTIVE).stream()
                .filter(m -> {
                    Integer lastContributedRound = m.getLastContributedRound();
                    return lastContributedRound == null || lastContributedRound < circle.getCurrentRound();
                })
                .toList();
    }

    /** Who receives this circle's current round payout: the active membership assigned to this round's position. */
    private CircleMembership getCurrentPayoutReceiver(Circle circle) {
        return circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.ACTIVE).stream()
                .filter(m -> {
                    Integer position = m.getPosition();
                    return position != null && position == circle.getCurrentRound();
                })
                .findFirst()
                .orElse(null);
    }
}
