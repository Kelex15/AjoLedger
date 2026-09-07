package com.ajoledger.backend.circle;

import com.ajoledger.backend.account.AccountService;
import com.ajoledger.backend.account.CircleAccountType;
import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.account.entity.CircleAccount;
import com.ajoledger.backend.account.entity.UserAccount;
import com.ajoledger.backend.account.repository.CircleAccountRepository;
import com.ajoledger.backend.account.repository.UserAccountRepository;
import com.ajoledger.backend.circle.dto.CircleCreateRequestDTO;
import com.ajoledger.backend.circle.exception.CircleActionNotAllowedException;
import com.ajoledger.backend.circle.exception.CircleCreatorRequiredException;
import com.ajoledger.backend.circle.exception.CircleNotFoundException;
import com.ajoledger.backend.circle.exception.InviteAlreadyRedeemedException;
import com.ajoledger.backend.circle.exception.InviteIdentityMismatchException;
import com.ajoledger.backend.circle.exception.InviteNotFoundException;
import com.ajoledger.backend.circle.policy.CircleSizingPolicy;
import com.ajoledger.backend.ledger.exception.InsufficientBalanceException;
import com.ajoledger.backend.system.Constants;
import com.ajoledger.backend.transaction.Leg;
import com.ajoledger.backend.transaction.Transaction;
import com.ajoledger.backend.transaction.TransactionService;
import com.ajoledger.backend.transaction.TransactionType;
import com.ajoledger.backend.transaction.exception.LienWithdrawalBlockedException;
import com.ajoledger.backend.user.User;
import com.ajoledger.backend.user.UserRepository;
import com.ajoledger.backend.user.exception.UserNotFoundException;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CircleService {

    private static final Logger log = LoggerFactory.getLogger(CircleService.class);

    private static final double BUFFER_FEE_RATE = 0.01;
    // Payout split: these can be tuned, they just need to sum to <= 1.
    private static final double PAYOUT_LIEN_RATE = 0.15;
    private static final double PAYOUT_BUFFER_FEE_RATE = 0.02;
    // Reputation score weighting: placeholders, not tuned.
    private static final double REPUTATION_LIEN_WEIGHT = 0.5;
    private static final long DEFAULT_PENALTY_MULTIPLIER = 3;
    private static final long REPUTATION_SMOOTHING_PRIOR = 1; // Laplace smoothing prior.

    private final TransactionService transactionService;
    private final UserAccountRepository userAccountRepository;
    private final CircleRepository circleRepository;
    private final CircleAccountRepository circleAccountRepository;
    private final CircleMembershipRepository circleMembershipRepository;
    private final CirclePayoutRepository circlePayoutRepository;
    private final CircleInviteRepository circleInviteRepository;
    private final Map<String, CircleSizingPolicy> sizingPoliciesByType;
    private final AccountService accountService;
    private final UserRepository userRepository;

    private CircleSizingPolicy resolveSizingPolicy(Circle circle) {
        return sizingPoliciesByType.get(circle.getSizingPolicyType().name());
    }

    private static BigDecimal roundAmount(BigDecimal amount) {
        return amount.setScale(Currency.USD.getDecimalScale(), RoundingMode.HALF_UP);
    }

    /** The creator funds their own round-1 contribution like any other member via joinCircle,
     * so creation rolls back entirely if they can't afford it. */
    @Transactional
    public Circle createCircle(UUID creatorUserId, CircleCreateRequestDTO dto) {
        User creator = userRepository.findById(creatorUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + creatorUserId));

        Circle circle = circleRepository.save(Circle.builder()
                .name(dto.name())
                .description(dto.description())
                .maxMembers(dto.maxMembers())
                .minMembers(dto.minMembers())
                .enrollmentDeadline(dto.enrollmentDeadline())
                .term(dto.term())
                .createdBy(creator)
                .contributionAmount(dto.contributionAmount())
                .currentRound(1)
                .status(CircleStatus.FORMING)
                .sizingPolicyType(dto.sizingPolicyType())
                .gated(dto.gated())
                .build());

        accountService.createCircleAccount(circle, CircleAccountType.POT);
        accountService.createCircleAccount(circle, CircleAccountType.RESERVE);
        joinCircle(creatorUserId, circle.getId());
        return circle;
    }

    public CircleInvite inviteToCircle(UUID circleId, UUID inviterUserId, String username, String email) {
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new CircleNotFoundException("Circle not found: " + circleId));
        if (!circle.isGated()) {
            throw new CircleActionNotAllowedException("Circle is not gated: " + circleId);
        }
        if (circle.getStatus() != CircleStatus.FORMING) {
            throw new CircleActionNotAllowedException("Circle is not open for invites: " + circleId);
        }
        if (!circle.getCreatedBy().getId().equals(inviterUserId)) {
            throw new CircleCreatorRequiredException("Only the circle's creator can invite members");
        }

        User inviter = circle.getCreatedBy();
        User invitedUser = null;
        if (username != null) {
            invitedUser = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
        } else if (email != null) {
            invitedUser = userRepository.findByEmail(email).orElse(null);
        }

        CircleInvite invite = CircleInvite.builder()
                .circle(circle)
                .invitedBy(inviter)
                .code(generateInviteCode())
                .invitedUser(invitedUser)
                .invitedEmail(invitedUser == null ? email : null)
                .build();
        return circleInviteRepository.save(invite);
    }

    @Transactional
    public Circle redeemInvite(String code, UUID redeemingUserId) {
        CircleInvite invite = circleInviteRepository.findByCode(code)
                .orElseThrow(() -> new InviteNotFoundException("Invite not found"));
        if (invite.getRedeemedAt() != null) {
            throw new InviteAlreadyRedeemedException("Invite has already been redeemed");
        }

        User redeemer = userRepository.findById(redeemingUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + redeemingUserId));

        if (invite.getInvitedUser() != null) {
            if (!invite.getInvitedUser().getId().equals(redeemingUserId)) {
                throw new InviteIdentityMismatchException("This invite was not issued to you");
            }
        } else {
            if (!invite.getInvitedEmail().equalsIgnoreCase(redeemer.getEmail())) {
                throw new InviteIdentityMismatchException("This invite was not issued to you");
            }
            invite.setInvitedUser(redeemer);
        }

        invite.setRedeemedAt(Instant.now());
        circleInviteRepository.save(invite);
        return invite.getCircle();
    }

    @Transactional
    public CircleMembership joinCircle(UUID userId, UUID circleId) {
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new CircleNotFoundException("Circle not found: " + circleId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        if (circleMembershipRepository.existsByUser_IdAndStatus(userId, MembershipStatus.ACTIVE)) {
            throw new CircleActionNotAllowedException("Already an active member of another circle");
        }

        if (!resolveSizingPolicy(circle).canMemberJoin(circle, user)) {
            throw new CircleActionNotAllowedException("Not eligible to join circle: " + circleId);
        }

        CircleMembership membership = circleMembershipRepository.save(CircleMembership.builder()
                .circle(circle)
                .user(user)
                .status(MembershipStatus.ACTIVE)
                .build());

        CircleAccount pot = circleAccountRepository.requireByCircleIdAndType(circleId, CircleAccountType.POT);
        postContribution(userId, circleId, pot.getId(), circle.getContributionAmount(),
                circleId + ":" + userId + ":join");
        membership.setLastContributedRound(circle.getCurrentRound());
        circleMembershipRepository.save(membership);

        int activeCount = circleMembershipRepository.findByCircle_IdAndStatus(circleId, MembershipStatus.ACTIVE).size();
        if (activeCount == circle.getMaxMembers()) {
            activateCircle(circle);
        }

        return membership;
    }

    /** Positions (payout order) are assigned here, at lock, by reputation score, highest first,
     * tiebroken by join order. */
    private void activateCircle(Circle circle) {
        List<CircleMembership> members = circleMembershipRepository
                .findByCircle_IdAndStatus(circle.getId(), MembershipStatus.ACTIVE);
        circle.setLockedMemberCount(members.size());

        // Scored once per member up front, not inside the comparator, since a sort can invoke
        // the comparator far more than once per element.
        @SuppressWarnings("null")
        Map<UUID, Double> scoresByMembershipId = members.stream()
                .collect(Collectors.toMap(CircleMembership::getId, m -> computeReputationScore(circle, m.getUser())));
        members.sort(Comparator
                .<CircleMembership, Double>comparing(m -> scoresByMembershipId.get(m.getId()))
                .reversed()
                .thenComparing(CircleMembership::getJoinedAt));
        for (int i = 0; i < members.size(); i++) {
            members.get(i).setPosition(i + 1);
            circleMembershipRepository.save(members.get(i));
        }

        circle.setStatus(CircleStatus.ACTIVE);
        circleRepository.save(circle);
        resolveRoundIfComplete(circle.getId());
    }

    /** Blends a lifetime behavioral track record with how much of this circle's worst-case
     * exposure the member's own LIEN already covers. Higher score gets an earlier position. */
    private double computeReputationScore(Circle circle, User user) {
        long successes = circleMembershipRepository.countByUser_IdAndStatus(user.getId(), MembershipStatus.COMPLETED);
        long withdrawals = circleMembershipRepository.countByUser_IdAndStatus(user.getId(), MembershipStatus.WITHDRAWN);
        long defaults = circleMembershipRepository.countByUser_IdAndStatus(user.getId(), MembershipStatus.DEFAULTED);
        long penalizedFailures = withdrawals + defaults * DEFAULT_PENALTY_MULTIPLIER;
        double smoothedSuccesses = successes + REPUTATION_SMOOTHING_PRIOR;
        double smoothedFailures = penalizedFailures + REPUTATION_SMOOTHING_PRIOR;
        double successRate = smoothedSuccesses / (smoothedSuccesses + smoothedFailures);

        double lienBalance = userAccountRepository.requireByUserIdAndType(user.getId(), UserAccountType.LIEN)
                .getBalance().doubleValue();
        double worstCaseExposure = circle.getContributionAmount().doubleValue()
                * (resolveSizingPolicy(circle).getTotalRounds(circle) - 1);
        double lienCoverageRatio = Math.min(1.0, lienBalance / worstCaseExposure);

        return REPUTATION_LIEN_WEIGHT * successRate + (1 - REPUTATION_LIEN_WEIGHT) * lienCoverageRatio;
    }

    private String generateInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    public Transaction postContribution(UUID userId, UUID circleId, UUID circlePotAccountId,
                                         BigDecimal amount, String idempotencyKey) {
        UserAccount savings = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.SAVINGS);
        UserAccount buffer = userAccountRepository.requireByUserIdAndType(
                Constants.Account.SYSTEM_USER_ID, UserAccountType.SYSTEM_BUFFER);
        BigDecimal fee = roundAmount(amount.multiply(BigDecimal.valueOf(BUFFER_FEE_RATE)));
        BigDecimal netToPot = amount.subtract(fee);
        List<Leg> legs = List.of(
                new Leg(savings.getId(), amount, BigDecimal.ZERO),
                new Leg(circlePotAccountId, BigDecimal.ZERO, netToPot),
                new Leg(buffer.getId(), BigDecimal.ZERO, fee));
        return transactionService.postLedgerTransaction(idempotencyKey, "CONTRIBUTION", TransactionType.CONTRIBUTION,
                amount, "Contribution to circle " + circleId, legs);
    }

    /** Exact inverse of postContribution's legs. Full original amount back, including the fee. */
    public Transaction postContributionRefund(UUID userId, UUID circleId, UUID circlePotAccountId,
                                               BigDecimal amount, String idempotencyKey) {
        UserAccount savings = userAccountRepository.requireByUserIdAndType(userId, UserAccountType.SAVINGS);
        UserAccount buffer = userAccountRepository.requireByUserIdAndType(
                Constants.Account.SYSTEM_USER_ID, UserAccountType.SYSTEM_BUFFER);
        BigDecimal fee = roundAmount(amount.multiply(BigDecimal.valueOf(BUFFER_FEE_RATE)));
        BigDecimal netFromPot = amount.subtract(fee);
        List<Leg> legs = List.of(
                new Leg(circlePotAccountId, netFromPot, BigDecimal.ZERO),
                new Leg(buffer.getId(), fee, BigDecimal.ZERO),
                new Leg(savings.getId(), BigDecimal.ZERO, amount));
        return transactionService.postLedgerTransaction(idempotencyKey, "CONTRIBUTION_REFUND",
                TransactionType.CONTRIBUTION_REFUND, amount, "Contribution refund for circle " + circleId, legs);
    }

    public Transaction postPayout(UUID circlePotAccountId, UUID circlePayoutId, UUID recipientUserId,
                                   BigDecimal grossAmount, String idempotencyKey) {
        UserAccount savings = userAccountRepository.requireByUserIdAndType(recipientUserId, UserAccountType.SAVINGS);
        UserAccount lien = userAccountRepository.requireByUserIdAndType(recipientUserId, UserAccountType.LIEN);
        UserAccount buffer = userAccountRepository.requireByUserIdAndType(
                Constants.Account.SYSTEM_USER_ID, UserAccountType.SYSTEM_BUFFER);

        BigDecimal lienShare = roundAmount(grossAmount.multiply(BigDecimal.valueOf(PAYOUT_LIEN_RATE)));
        BigDecimal bufferFee = roundAmount(grossAmount.multiply(BigDecimal.valueOf(PAYOUT_BUFFER_FEE_RATE)));
        BigDecimal net = grossAmount.subtract(lienShare).subtract(bufferFee);

        List<Leg> legs = List.of(
                new Leg(circlePotAccountId, grossAmount, BigDecimal.ZERO),
                new Leg(savings.getId(), BigDecimal.ZERO, net),
                new Leg(lien.getId(), BigDecimal.ZERO, lienShare),
                new Leg(buffer.getId(), BigDecimal.ZERO, bufferFee));
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

    /** Default waterfall (design/v1.md §5): lien seized first, then the buffer covers the rest.
     * Both legs credit the circle's reserve account, released gradually into the pot at each
     * round's resolution instead of landing as a lump sum on whichever round resolves next. */
    public List<Transaction> postDefaultWaterfall(UUID userId, UUID circleMembershipId, UUID circleReserveAccountId,
                                                    BigDecimal groupLoss, String idempotencyKey) {
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
                    new Leg(circleReserveAccountId, BigDecimal.ZERO, seizedFromLien));
            transactions.add(transactionService.postLedgerTransaction(idempotencyKey + ":lien-seizure", "LIEN_SEIZURE",
                    TransactionType.LIEN_SEIZURE, seizedFromLien,
                    "Lien seizure for membership " + circleMembershipId, seizureLegs));
        }

        if (remainingLoss.signum() > 0) {
            BigDecimal bufferPayout = buffer.getBalance().min(remainingLoss);
            if (bufferPayout.signum() > 0) {
                List<Leg> bufferLegs = List.of(
                        new Leg(buffer.getId(), bufferPayout, BigDecimal.ZERO),
                        new Leg(circleReserveAccountId, BigDecimal.ZERO, bufferPayout));
                transactions.add(transactionService.postLedgerTransaction(idempotencyKey + ":buffer-payout", "BUFFER_PAYOUT_TO_COVER_DEFAULT",
                        TransactionType.BUFFER_PAYOUT_TO_COVER_DEFAULT, bufferPayout,
                        "Buffer payout covering default for membership " + circleMembershipId, bufferLegs));
            }
            flagUnrecoveredShortfall(circleMembershipId, remainingLoss.subtract(bufferPayout));
        }

        return transactions;
    }

    /** GROUP_SHORTFALL_ABSORPTION isn't designed yet (design/v1.md §5). Logged, not thrown,
     * since the waterfall itself did all it could. */
    private void flagUnrecoveredShortfall(UUID circleMembershipId, BigDecimal unrecoveredShortfall) {
        if (unrecoveredShortfall.signum() > 0) {
            log.warn("Circle default shortfall not fully covered for membership {}: ${} unrecovered after lien + buffer",
                    circleMembershipId, unrecoveredShortfall);
        }
    }

    /** Refunds every current member's round-1 contribution in full. The circle never activated. */
    private void dissolveCircle(Circle circle) {
        UUID circleId = circle.getId();
        CircleAccount pot = circleAccountRepository.requireByCircleIdAndType(circleId, CircleAccountType.POT);

        for (CircleMembership membership : circleMembershipRepository.findByCircle_IdAndStatus(circleId, MembershipStatus.ACTIVE)) {
            UUID userId = membership.getUser().getId();
            postContributionRefund(userId, circleId, pot.getId(), circle.getContributionAmount(),
                    circleId + ":" + userId + ":dissolve-refund");
            membership.setStatus(MembershipStatus.DISSOLVED);
            circleMembershipRepository.save(membership);
        }

        circle.setStatus(CircleStatus.DISSOLVED);
        circleRepository.save(circle);
    }

    /** Creator-initiated cancellation of any still-FORMING circle, fixed-size included. */
    @Transactional
    public Circle cancelCircle(UUID circleId, UUID requestingUserId) {
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new CircleNotFoundException("Circle not found: " + circleId));
        if (!circle.getCreatedBy().getId().equals(requestingUserId)) {
            throw new CircleCreatorRequiredException("Only the circle's creator can cancel it");
        }
        if (circle.getStatus() != CircleStatus.FORMING) {
            throw new CircleActionNotAllowedException("Circle can only be cancelled while still forming: " + circleId);
        }
        dissolveCircle(circle);
        return circle;
    }

    // ---- scheduler interface: a scheduled job fetches due circles, then processes each one ----

    /** FIXED_SIZE circles never have an enrollmentDeadline, so they're naturally excluded here. */
    @SuppressWarnings("null")
    public List<UUID> findCirclesWithClosedEnrollment() {
        Instant now = Instant.now();
        return circleRepository.findByStatusAndEnrollmentDeadlineIsNotNull(CircleStatus.FORMING).stream()
                .filter(circle -> !circle.getEnrollmentDeadline().isAfter(now))
                .map(Circle::getId)
                .toList();
    }

    /** Locks in whoever joined by the deadline if there's enough of them, otherwise dissolves. */
    @Transactional
    public void closeEnrollment(UUID circleId) {
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new CircleNotFoundException("Circle not found: " + circleId));
        int activeCount = circleMembershipRepository.findByCircle_IdAndStatus(circleId, MembershipStatus.ACTIVE).size();
        int effectiveMinimum = circle.getMinMembers() != null ? circle.getMinMembers() : Constants.Circle.DEFAULT_MIN_MEMBERS;

        if (activeCount >= effectiveMinimum) {
            activateCircle(circle);
        } else {
            dissolveCircle(circle);
        }
    }

    /** The scheduler is blind to how "due" is decided. It just gets ids to process. */
    @SuppressWarnings("null")
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
        List<CircleMembership> unpaid = unpaidActiveMemberships(circle);

        for (CircleMembership membership : unpaid) {
            try {
                postContribution(membership.getUser().getId(), circleId, pot.getId(), circle.getContributionAmount(),
                        circleId + ":" + membership.getId() + ":round-" + circle.getCurrentRound());
                membership.setLastContributedRound(circle.getCurrentRound());
                circleMembershipRepository.save(membership);
            } catch (InsufficientBalanceException e) {
                // left unpaid. markNonPayers handles it next.
            }
        }
    }

    private void markNonPayers(UUID circleId) {
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new IllegalStateException("Circle not found: " + circleId));
        CircleAccount reserve = circleAccountRepository.requireByCircleIdAndType(circleId, CircleAccountType.RESERVE);
        @SuppressWarnings("null")
        List<UUID> unpaidMembershipIds = unpaidActiveMemberships(circle).stream().map(CircleMembership::getId).toList();

        for (UUID membershipId : unpaidMembershipIds) {
            // Re-fetched since an earlier withdrawal in this same loop may have shifted this
            // membership's position.
            CircleMembership membership = circleMembershipRepository.findById(membershipId).orElseThrow();
            User user = membership.getUser();
            boolean alreadyPaidOut = circlePayoutRepository.existsByCircle_IdAndRecipient_Id(circleId, user.getId());

            if (alreadyPaidOut) {
                int position = membership.getPosition() != null ? membership.getPosition() : 0;
                BigDecimal groupLoss = resolveSizingPolicy(circle).calculatePotentialGroupLoss(circle, user, position);

                // Waterfall runs first, membership only flips to DEFAULTED once funds are actually
                // seized. If this throws, the membership stays ACTIVE so the next scheduler pass
                // retries instead of leaving a defaulted membership with nothing recovered.
                postDefaultWaterfall(user.getId(), membership.getId(), reserve.getId(), groupLoss,
                        circleId + ":" + membership.getId() + ":default-round-" + circle.getCurrentRound());

                membership.setStatus(MembershipStatus.DEFAULTED);
                circleMembershipRepository.save(membership);
            } else {
                int withdrawnPosition = membership.getPosition();
                membership.setStatus(MembershipStatus.WITHDRAWN);
                circleMembershipRepository.save(membership);
                compactPositionsAfterWithdrawal(circle, withdrawnPosition);
            }
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

    private void resolveRoundIfComplete(UUID circleId) {
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
            User recipient = recipientMembership.getUser();
            releaseReserveShare(circle);
            // Fetched after the reserve release, whose JDBC-side balance update this JPA read must see.
            CircleAccount pot = circleAccountRepository.requireByCircleIdAndType(circleId, CircleAccountType.POT);
            // Pay out what the pot actually holds, not calculatePotSize's theoretical figure. The
            // pot never holds the full contributionAmount because postContribution skims a fee
            // off each contribution before crediting it.
            BigDecimal grossAmount = pot.getBalance();

            CirclePayout payout = circlePayoutRepository.save(CirclePayout.builder()
                    .circle(circle)
                    .recipient(recipient)
                    .roundNumber(circle.getCurrentRound())
                    .build());

            postPayout(pot.getId(), payout.getId(), recipient.getId(), grossAmount,
                    circleId + ":" + recipientMembership.getId() + ":payout-round-" + circle.getCurrentRound());

            circle.setCurrentRound(circle.getCurrentRound() + 1);
            circle.setLastRoundAt(Instant.now());
            circleRepository.save(circle);
        }

        // Checked regardless of whether this call paid anyone: a withdrawal can vacate the
        // circle's last remaining position with nobody left to shift into it, so a round with
        // no recipient still needs to be recognized as "everyone left is already paid."
        boolean everyonePaid = activeMembers.stream()
                .allMatch(m -> circlePayoutRepository.existsByCircle_IdAndRecipient_Id(circleId, m.getUser().getId()));
        if (everyonePaid) {
            completeCircle(circle, resolveSizingPolicy(circle).getTotalRounds(circle));
        }
    }

    private void completeCircle(Circle circle, int totalRounds) {
        circle.setStatus(CircleStatus.COMPLETED);
        circle.setCurrentRound(totalRounds);
        circleRepository.save(circle);
        for (CircleMembership membership : circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.ACTIVE)) {
            membership.setStatus(MembershipStatus.COMPLETED);
            circleMembershipRepository.save(membership);
        }
    }

    /** Spreads the reserve's current balance evenly across the rounds still ahead, rather than
     * dumping a recovered default into whichever round resolves next. */
    private void releaseReserveShare(Circle circle) {
        UUID circleId = circle.getId();
        CircleAccount reserve = circleAccountRepository.requireByCircleIdAndType(circleId, CircleAccountType.RESERVE);
        BigDecimal reserveBalance = reserve.getBalance();
        if (reserveBalance.signum() <= 0) {
            return;
        }

        CircleAccount pot = circleAccountRepository.requireByCircleIdAndType(circleId, CircleAccountType.POT);
        int roundsRemaining = resolveSizingPolicy(circle).getTotalRounds(circle) - circle.getCurrentRound() + 1;
        BigDecimal releaseAmount = roundAmount(reserveBalance.divide(BigDecimal.valueOf(roundsRemaining), 10, RoundingMode.HALF_UP));

        List<Leg> legs = List.of(
                new Leg(reserve.getId(), releaseAmount, BigDecimal.ZERO),
                new Leg(pot.getId(), BigDecimal.ZERO, releaseAmount));
        transactionService.postLedgerTransaction(circleId + ":reserve-release-round-" + circle.getCurrentRound(),
                "CIRCLE_RESERVE_DISBURSEMENT", TransactionType.CIRCLE_RESERVE_DISBURSEMENT, releaseAmount,
                "Reserve release into pot for round " + circle.getCurrentRound(), legs);
    }

    private List<CircleMembership> unpaidActiveMemberships(Circle circle) {
        return circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.ACTIVE).stream()
                .filter(m -> m.getLastContributedRound() == null || m.getLastContributedRound() < circle.getCurrentRound())
                .toList();
    }

    /** Who receives this circle's current round payout: the active membership assigned to this round's position. */
    private CircleMembership getCurrentPayoutReceiver(Circle circle) {
        return circleMembershipRepository.findByCircle_IdAndStatus(circle.getId(), MembershipStatus.ACTIVE).stream()
                .filter(m -> m.getPosition() != null && m.getPosition() == circle.getCurrentRound())
                .findFirst()
                .orElse(null);
    }
}
