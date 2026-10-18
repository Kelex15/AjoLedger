package com.ajoledger.backend.circle;

import com.ajoledger.backend.account.AccountService;
import com.ajoledger.backend.account.CircleAccountType;
import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.account.entity.CircleAccount;
import com.ajoledger.backend.account.entity.UserAccount;
import com.ajoledger.backend.account.exception.AccountNotFoundException;
import com.ajoledger.backend.account.repository.CircleAccountRepository;
import com.ajoledger.backend.account.repository.UserAccountRepository;
import com.ajoledger.backend.circle.dto.CircleCreateRequestDTO;
import com.ajoledger.backend.circle.entity.Circle;
import com.ajoledger.backend.circle.entity.CircleInvite;
import com.ajoledger.backend.circle.entity.CircleMemberDebitAuthorization;
import com.ajoledger.backend.circle.entity.CircleMembership;
import com.ajoledger.backend.circle.entity.CirclePayout;
import com.ajoledger.backend.circle.exception.CircleActionNotAllowedException;
import com.ajoledger.backend.circle.exception.CircleCreatorRequiredException;
import com.ajoledger.backend.circle.exception.CircleNotFoundException;
import com.ajoledger.backend.circle.exception.InviteAlreadyRedeemedException;
import com.ajoledger.backend.circle.exception.InviteIdentityMismatchException;
import com.ajoledger.backend.circle.exception.InviteNotFoundException;
import com.ajoledger.backend.circle.policy.CircleSizingPolicy;
import com.ajoledger.backend.circle.repository.CircleInviteRepository;
import com.ajoledger.backend.circle.repository.CircleMemberDebitAuthorizationRepository;
import com.ajoledger.backend.circle.repository.CircleMembershipRepository;
import com.ajoledger.backend.circle.repository.CirclePayoutRepository;
import com.ajoledger.backend.circle.repository.CircleRepository;
import com.ajoledger.backend.email.EmailSender;
import com.ajoledger.backend.ledger.LedgerDao;
import com.ajoledger.backend.ledger.idempotency.IdempotencyKeys;
import com.ajoledger.backend.system.Constants;
import com.ajoledger.backend.user.User;
import com.ajoledger.backend.user.UserRepository;
import com.ajoledger.backend.user.exception.UserNotFoundException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CircleService {

    // Reputation score weighting.
    private static final double REPUTATION_LIEN_WEIGHT = 0.5;
    private static final long DEFAULT_PENALTY_MULTIPLIER = 3;
    private static final long REPUTATION_SMOOTHING_PRIOR = 1; // Laplace smoothing prior.

    private final UserAccountRepository userAccountRepository;
    private final CircleRepository circleRepository;
    private final CircleAccountRepository circleAccountRepository;
    private final CircleMembershipRepository circleMembershipRepository;
    private final CircleInviteRepository circleInviteRepository;
    private final CirclePayoutRepository circlePayoutRepository;
    private final CircleMemberDebitAuthorizationRepository circleMemberDebitAuthorizationRepository;
    private final Map<String, CircleSizingPolicy> sizingPoliciesByType;
    private final AccountService accountService;
    private final UserRepository userRepository;
    private final LedgerDao ledgerDao;
    private final CircleTransactionsService circleTransactionsService;
    private final CircleRoundService circleRoundService;
    private final EmailSender emailSender;

    private CircleSizingPolicy resolveSizingPolicy(Circle circle) {
        return sizingPoliciesByType.get(circle.getSizingPolicyType().name());
    }

    /** Locked circles use the real round count; a still-FORMING circle has no lockedMemberCount
     * yet, so maxMembers is the best available pre-lock estimate. */
    public int getTotalRounds(Circle circle) {
        return circle.getLockedMemberCount() != null
                ? resolveSizingPolicy(circle).getTotalRounds(circle)
                : circle.getMaxMembers();
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
                .minReputationScore(dto.minReputationScore())
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
        invite = circleInviteRepository.save(invite);

        String toEmail = invitedUser != null ? invitedUser.getEmail() : email;
        emailSender.sendDefaultNotificationEmail(toEmail,
                "You've been invited to join the circle \"" + circle.getName() + "\". Invite code: " + invite.getCode());
        return invite;
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

        if (!resolveSizingPolicy(circle).canAdmitMember(circle, user)) {
            throw new CircleActionNotAllowedException("Not eligible to join circle: " + circleId);
        }

        boolean isCreator = circle.getCreatedBy().getId().equals(userId);
        if (circle.getMinReputationScore() != null && !isCreator
                && computeReputationScore(circle, user) < circle.getMinReputationScore()) {
            throw new CircleActionNotAllowedException("Reputation score below this circle's minimum: " + circleId);
        }

        CircleMembership membership = circleMembershipRepository.save(CircleMembership.builder()
                .circle(circle)
                .user(user)
                .status(MembershipStatus.ACTIVE)
                .build());

        CircleAccount pot = circleAccountRepository.requireByCircleIdAndType(circleId, CircleAccountType.POT);
        // Keyed by this membership row's id, since each rejoin cycle needs its own idempotency record.
        circleTransactionsService.postContribution(userId, circleId, pot.getId(), circle.getContributionAmount(),
                IdempotencyKeys.of(membership.getId(), "join"));
        BigDecimal collateral = calculateJoinCollateral(circle);
        circleTransactionsService.postLienHold(userId, collateral, IdempotencyKeys.of(membership.getId(), "join-collateral"));
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

        for (CircleMemberStanding standing : rankByReputation(circle, members)) {
            CircleMembership membership = standing.membership();
            membership.setPosition(standing.payoutPosition());
            circleMembershipRepository.save(membership);

            BigDecimal initialCeiling = resolveSizingPolicy(circle)
                    .calculateRealizedGroupLoss(circle, members.size(), standing.payoutPosition());
            circleMemberDebitAuthorizationRepository.save(CircleMemberDebitAuthorization.builder()
                    .circleMembership(membership)
                    .remainingCeiling(initialCeiling)
                    .build());
        }

        circle.setStatus(CircleStatus.ACTIVE);
        circleRepository.save(circle);
        circleRoundService.resolveRoundIfComplete(circle.getId());
    }

    /** Live preview: recomputed fresh each call against whoever's currently active, so it shifts
     * both pre-lock (as members join) and post-lock (as reputation keeps evolving after the real
     * position froze), so it can diverge from the membership's own stored position either way. */
    public List<CircleMemberStanding> getMemberStandings(UUID circleId) {
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new CircleNotFoundException("Circle not found: " + circleId));
        List<CircleMembership> members = circleMembershipRepository
                .findByCircle_IdAndStatus(circleId, MembershipStatus.ACTIVE);
        return rankByReputation(circle, members);
    }

    public Set<UUID> getPaidRecipientIds(UUID circleId) {
        return circleRoundService.getPaidRecipientIds(circleId);
    }

    private List<CircleMemberStanding> rankByReputation(Circle circle, List<CircleMembership> members) {
        Map<UUID, Double> scoresByMembershipId = computeReputationScores(circle, members);
        members.sort(Comparator
                .<CircleMembership, Double>comparing(m -> scoresByMembershipId.get(m.getId()))
                .reversed()
                .thenComparing(CircleMembership::getJoinedAt));

        List<CircleMemberStanding> standings = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            CircleMembership member = members.get(i);
            standings.add(new CircleMemberStanding(member, scoresByMembershipId.get(member.getId()), i + 1));
        }
        return standings;
    }

    /** Blends a lifetime behavioral track record with how much of this circle's worst-case
     * exposure the member's own LIEN already covers. Higher score gets an earlier position. */
    public double computeReputationScore(Circle circle, User user) {
        long successes = circleMembershipRepository.countByUser_IdAndStatus(user.getId(), MembershipStatus.COMPLETED);
        long withdrawals = circleMembershipRepository.countByUser_IdAndStatus(user.getId(), MembershipStatus.WITHDRAWN);
        long defaults = circleMembershipRepository.countByUser_IdAndStatus(user.getId(), MembershipStatus.DEFAULTED);
        UserAccount lienAccount = userAccountRepository.requireByUserIdAndType(user.getId(), UserAccountType.LIEN);
        double lienBalance = ledgerDao.getBalance(lienAccount.getId()).doubleValue();
        return computeReputationScore(circle, successes, withdrawals, defaults, lienBalance);
    }

    private Map<UUID, Double> computeReputationScores(Circle circle, List<CircleMembership> members) {
        List<UUID> userIds = members.stream().map(m -> m.getUser().getId()).toList();

        Map<UUID, Map<MembershipStatus, Long>> statusCountsByUserId = circleMembershipRepository
                .countByUser_IdInAndStatusIn(userIds,
                        List.of(MembershipStatus.COMPLETED, MembershipStatus.WITHDRAWN, MembershipStatus.DEFAULTED))
                .stream()
                .collect(Collectors.groupingBy(CircleMembershipRepository.UserStatusCount::getUserId,
                        Collectors.toMap(CircleMembershipRepository.UserStatusCount::getStatus,
                                CircleMembershipRepository.UserStatusCount::getCount)));

        Map<UUID, UUID> lienAccountIdByUserId = userAccountRepository
                .findByUserIdInAndUserAccountType(userIds, UserAccountType.LIEN).stream()
                .collect(Collectors.toMap(account -> account.getUser().getId(), UserAccount::getId));
        for (UUID userId : userIds) {
            if (!lienAccountIdByUserId.containsKey(userId)) {
                throw new AccountNotFoundException("No " + UserAccountType.LIEN + " account for user " + userId);
            }
        }

        Map<UUID, BigDecimal> lienBalanceByAccountId = ledgerDao.getBalances(List.copyOf(lienAccountIdByUserId.values()));

        Map<UUID, Double> scoresByMembershipId = new HashMap<>();
        for (CircleMembership member : members) {
            UUID userId = member.getUser().getId();
            Map<MembershipStatus, Long> statusCounts = statusCountsByUserId.getOrDefault(userId, Map.of());
            long successes = statusCounts.getOrDefault(MembershipStatus.COMPLETED, 0L);
            long withdrawals = statusCounts.getOrDefault(MembershipStatus.WITHDRAWN, 0L);
            long defaults = statusCounts.getOrDefault(MembershipStatus.DEFAULTED, 0L);
            BigDecimal lienBalance = lienBalanceByAccountId.getOrDefault(lienAccountIdByUserId.get(userId), BigDecimal.ZERO);
            scoresByMembershipId.put(member.getId(),
                    computeReputationScore(circle, successes, withdrawals, defaults, lienBalance.doubleValue()));
        }
        return scoresByMembershipId;
    }

    private double computeReputationScore(Circle circle, long successes, long withdrawals, long defaults, double lienBalance) {
        long penalizedFailures = TrustCalculations.calculatePenalizedFailures(withdrawals, defaults, DEFAULT_PENALTY_MULTIPLIER);
        double successRate = TrustCalculations.calculateSuccessRate(successes, penalizedFailures, REPUTATION_SMOOTHING_PRIOR);

        int totalRounds = getTotalRounds(circle);
        double worstCaseExposure = circle.getContributionAmount().doubleValue() * (totalRounds - 1);
        double lienCoverageRatio = TrustCalculations.calculateLienCoverageRatio(lienBalance, worstCaseExposure);

        return TrustCalculations.calculateReputationScore(successRate, lienCoverageRatio, REPUTATION_LIEN_WEIGHT);
    }

    private String generateInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    /** Refunds every current member's round-1 contribution in full. The circle never activated. */
    private void dissolveCircle(Circle circle) {
        UUID circleId = circle.getId();
        CircleAccount pot = circleAccountRepository.requireByCircleIdAndType(circleId, CircleAccountType.POT);

        for (CircleMembership membership : circleMembershipRepository.findByCircle_IdAndStatus(circleId, MembershipStatus.ACTIVE)) {
            UUID userId = membership.getUser().getId();
            circleTransactionsService.postContributionRefund(userId, circleId, pot.getId(), circle.getContributionAmount(),
                    IdempotencyKeys.of(membership.getId(), "dissolve-refund"));
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

    public Circle getMyCircle(UUID userId) {
        return circleMembershipRepository.findByUser_IdAndStatus(userId, MembershipStatus.ACTIVE)
                .map(CircleMembership::getCircle)
                .orElseThrow(() -> new CircleNotFoundException("No active circle for user: " + userId));
    }

    public List<CircleMembership> getMyMemberships(UUID userId) {
        return circleMembershipRepository.findByUser_IdOrderByJoinedAtDesc(userId);
    }

    public Circle getCircle(UUID circleId) {
        return circleRepository.findById(circleId)
                .orElseThrow(() -> new CircleNotFoundException("Circle not found: " + circleId));
    }

    public List<CirclePayout> getPayouts(UUID circleId) {
        return circlePayoutRepository.findByCircle_IdOrderByRoundNumberAsc(circleId);
    }

    public List<Circle> getBrowsableCircles(UUID userId) {
        return circleRepository.findByStatus(CircleStatus.FORMING).stream()
                .filter(circle -> !circleMembershipRepository.existsByCircle_IdAndUser_Id(circle.getId(), userId))
                .toList();
    }

    public BigDecimal calculateJoinCollateral(Circle circle) {
        return roundAmount(resolveSizingPolicy(circle).calculateJoinCollateral(circle));
    }

    public Currency getCircleCurrency(UUID circleId) {
        return circleAccountRepository.requireByCircleIdAndType(circleId, CircleAccountType.POT).getCurrency();
    }

    public List<CircleMembership> getActiveMembers(UUID circleId) {
        if (!circleRepository.existsById(circleId)) {
            throw new CircleNotFoundException("Circle not found: " + circleId);
        }
        return circleMembershipRepository.findByCircle_IdAndStatus(circleId, MembershipStatus.ACTIVE);
    }

    /** Creator-initiated removal of a not-yet-locked member. */
    @Transactional
    public void removeMember(UUID circleId, UUID requestingUserId, UUID memberUserId) {
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new CircleNotFoundException("Circle not found: " + circleId));
        if (!circle.getCreatedBy().getId().equals(requestingUserId)) {
            throw new CircleCreatorRequiredException("Only the circle's creator can remove a member");
        }
        if (circle.getStatus() != CircleStatus.FORMING) {
            throw new CircleActionNotAllowedException("Members can only be removed while the circle is still forming: " + circleId);
        }
        if (memberUserId.equals(requestingUserId)) {
            throw new CircleActionNotAllowedException("The creator cannot remove themselves; cancel the circle instead");
        }
        CircleMembership membership = circleMembershipRepository
                .findByCircle_IdAndUser_IdAndStatus(circleId, memberUserId, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new CircleActionNotAllowedException("User is not an active member of this circle: " + memberUserId));

        CircleAccount pot = circleAccountRepository.requireByCircleIdAndType(circleId, CircleAccountType.POT);
        // Keyed by this membership row's id, since each rejoin cycle needs its own idempotency record.
        circleTransactionsService.postContributionRefund(memberUserId, circleId, pot.getId(), circle.getContributionAmount(),
                IdempotencyKeys.of(membership.getId(), "remove-refund"));
        circleMembershipRepository.delete(membership);
    }

    /** Creator-initiated and permanent, unlike removeMember: blocks the member from ever
     * rejoining this circle. */
    @Transactional
    public void banMember(UUID circleId, UUID requestingUserId, UUID memberUserId) {
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new CircleNotFoundException("Circle not found: " + circleId));
        if (!circle.getCreatedBy().getId().equals(requestingUserId)) {
            throw new CircleCreatorRequiredException("Only the circle's creator can ban a member");
        }
        if (circle.getStatus() != CircleStatus.FORMING) {
            throw new CircleActionNotAllowedException("Members can only be banned while the circle is still forming: " + circleId);
        }
        if (memberUserId.equals(requestingUserId)) {
            throw new CircleActionNotAllowedException("The creator cannot ban themselves; cancel the circle instead");
        }
        CircleMembership membership = circleMembershipRepository
                .findByCircle_IdAndUser_IdAndStatus(circleId, memberUserId, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new CircleActionNotAllowedException("User is not an active member of this circle: " + memberUserId));

        CircleAccount pot = circleAccountRepository.requireByCircleIdAndType(circleId, CircleAccountType.POT);
        circleTransactionsService.postContributionRefund(memberUserId, circleId, pot.getId(), circle.getContributionAmount(),
                IdempotencyKeys.of(membership.getId(), "ban-refund"));
        membership.setStatus(MembershipStatus.BANNED);
        circleMembershipRepository.save(membership);
    }

    /** Self-initiated exit before lock, full contribution refund, collateral untouched, no reputation penalty */
    @Transactional
    public void leaveCircle(UUID circleId, UUID userId) {
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new CircleNotFoundException("Circle not found: " + circleId));
        if (circle.getStatus() != CircleStatus.FORMING) {
            throw new CircleActionNotAllowedException("Members can only leave while the circle is still forming: " + circleId);
        }
        if (circle.getCreatedBy().getId().equals(userId)) {
            throw new CircleActionNotAllowedException("The creator cannot leave; cancel the circle instead");
        }
        CircleMembership membership = circleMembershipRepository
                .findByCircle_IdAndUser_IdAndStatus(circleId, userId, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new CircleActionNotAllowedException("Not an active member of this circle: " + userId));

        CircleAccount pot = circleAccountRepository.requireByCircleIdAndType(circleId, CircleAccountType.POT);
        circleTransactionsService.postContributionRefund(userId, circleId, pot.getId(), circle.getContributionAmount(),
                IdempotencyKeys.of(membership.getId(), "leave-refund"));
        circleMembershipRepository.delete(membership);
    }

    // ---- scheduler interface: CircleEnrollmentScheduler fetches due circles, then processes each one ----

    /** FIXED_SIZE circles never have an enrollmentDeadline, so they're naturally excluded here. */
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
}
