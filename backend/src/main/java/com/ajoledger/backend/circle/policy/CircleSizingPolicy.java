package com.ajoledger.backend.circle.policy;

import java.math.BigDecimal;

import com.ajoledger.backend.circle.Circle;
import com.ajoledger.backend.circle.CircleInviteRepository;
import com.ajoledger.backend.circle.CircleMembershipRepository;
import com.ajoledger.backend.circle.CircleStatus;
import com.ajoledger.backend.circle.MembershipStatus;
import com.ajoledger.backend.circle.TrustCalculations;
import com.ajoledger.backend.user.User;


public abstract class CircleSizingPolicy {

    // Placeholders, not tuned yet.
    private static final BigDecimal GATED_LOSS_DISCOUNT = new BigDecimal("0.5");
    private static final double JOIN_COLLATERAL_RATE = 0.10;

    protected final CircleInviteRepository circleInviteRepository;
    protected final CircleMembershipRepository circleMembershipRepository;

    protected CircleSizingPolicy(CircleInviteRepository circleInviteRepository,
                                  CircleMembershipRepository circleMembershipRepository) {
        this.circleInviteRepository = circleInviteRepository;
        this.circleMembershipRepository = circleMembershipRepository;
    }

    public boolean canMemberJoin(Circle circle, User candidate) {
        if (circle.getStatus() != CircleStatus.FORMING || !hasEnrollmentRoom(circle)) {
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

    /** Flat, uniform join-time hold, unrelated to eventual payout position (not known until
     * lock). Safe to call pre-lock, unlike calculatePotentialGroupLoss/getTotalRounds. */
    public BigDecimal calculateJoinCollateral(Circle circle) {
        BigDecimal base = circle.getContributionAmount().multiply(BigDecimal.valueOf(JOIN_COLLATERAL_RATE));
        return TrustCalculations.applyGatedDiscount(base, circle.isGated(), GATED_LOSS_DISCOUNT);
    }
}
