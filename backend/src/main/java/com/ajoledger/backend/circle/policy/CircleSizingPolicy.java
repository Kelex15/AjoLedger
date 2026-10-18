package com.ajoledger.backend.circle.policy;

import java.math.BigDecimal;

import com.ajoledger.backend.circle.CircleStatus;
import com.ajoledger.backend.circle.MembershipStatus;
import com.ajoledger.backend.circle.TrustCalculations;
import com.ajoledger.backend.circle.entity.Circle;
import com.ajoledger.backend.circle.repository.CircleInviteRepository;
import com.ajoledger.backend.circle.repository.CircleMembershipRepository;
import com.ajoledger.backend.user.User;


public abstract class CircleSizingPolicy {

    private static final BigDecimal GATED_LOSS_DISCOUNT = new BigDecimal("0.5");
    private static final double JOIN_COLLATERAL_RATE = 0.10;

    protected final CircleInviteRepository circleInviteRepository;
    protected final CircleMembershipRepository circleMembershipRepository;

    protected CircleSizingPolicy(CircleInviteRepository circleInviteRepository,
                                  CircleMembershipRepository circleMembershipRepository) {
        this.circleInviteRepository = circleInviteRepository;
        this.circleMembershipRepository = circleMembershipRepository;
    }

    public boolean canAdmitMember(Circle circle, User candidate) {
        if (circle.getStatus() != CircleStatus.FORMING || !hasEnrollmentRoom(circle)) {
            return false;
        }
        if (circleMembershipRepository.existsByCircle_IdAndUser_IdAndStatus(
                circle.getId(), candidate.getId(), MembershipStatus.BANNED)) {
            return false;
        }
        boolean isCreator = circle.getCreatedBy().getId().equals(candidate.getId());
        if (circle.isGated() && !isCreator) {
            return circleInviteRepository.existsByCircle_IdAndInvitedUser_IdAndRedeemedAtIsNotNull(
                    circle.getId(), candidate.getId());
        }
        return true;
    }

    protected abstract boolean hasEnrollmentRoom(Circle circle);

    protected boolean isBelowCap(Circle circle) {
        int activeMemberCount = circleMembershipRepository
                .findByCircle_IdAndStatus(circle.getId(), MembershipStatus.ACTIVE).size();
        return activeMemberCount < circle.getMaxMembers();
    }

    public BigDecimal calculatePotSize(Circle circle) {
        return circle.getContributionAmount().multiply(BigDecimal.valueOf(circle.getLockedMemberCount()));
    }

    public int getTotalRounds(Circle circle) {
        return circle.getLockedMemberCount();
    }

    public BigDecimal calculatePotentialGroupLoss(Circle circle, User member, int payoutPosition) {
        BigDecimal loss = TrustCalculations.calculateGroupLoss(circle.getContributionAmount(),
                circle.getLockedMemberCount(), payoutPosition);
        return TrustCalculations.applyGatedDiscount(loss, circle.isGated(), GATED_LOSS_DISCOUNT);
    }

    /** Never gated-discounted: a real default owes the full contribution regardless of gating.
     * totalRounds is passed explicitly rather than read off circle, since a co-occurring
     * withdrawal in the same pass can shrink circle.getLockedMemberCount() before this runs. */
    public BigDecimal calculateRealizedGroupLoss(Circle circle, int totalRounds, int payoutPosition) {
        return TrustCalculations.calculateGroupLoss(circle.getContributionAmount(), totalRounds, payoutPosition);
    }

    /** Flat, uniform join-time hold, unrelated to eventual payout position (not known until
     * lock). Safe to call pre-lock, unlike calculatePotentialGroupLoss/getTotalRounds. */
    public BigDecimal calculateJoinCollateral(Circle circle) {
        BigDecimal base = circle.getContributionAmount().multiply(BigDecimal.valueOf(JOIN_COLLATERAL_RATE));
        return TrustCalculations.applyGatedDiscount(base, circle.isGated(), GATED_LOSS_DISCOUNT);
    }
}
