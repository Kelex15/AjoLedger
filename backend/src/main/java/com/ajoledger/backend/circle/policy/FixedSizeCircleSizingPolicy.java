package com.ajoledger.backend.circle.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import com.ajoledger.backend.circle.Circle;
import com.ajoledger.backend.circle.CircleInviteRepository;
import com.ajoledger.backend.circle.CircleMembershipRepository;
import com.ajoledger.backend.circle.CircleStatus;
import com.ajoledger.backend.circle.MembershipStatus;
import com.ajoledger.backend.user.User;

@Component
@RequiredArgsConstructor
public class FixedSizeCircleSizingPolicy implements CircleSizingPolicy {

    // Placeholder pending real tuning - a gated circle's handpicked membership carries real
    // risk information the platform doesn't otherwise have, so its loss exposure is discounted.
    private static final BigDecimal GATED_LOSS_DISCOUNT = new BigDecimal("0.5");

    private final CircleInviteRepository circleInviteRepository;
    private final CircleMembershipRepository circleMembershipRepository;

    @Override
    public boolean canMemberJoin(Circle circle, User candidate) {
        int activeMemberCount = circleMembershipRepository
                .findByCircle_IdAndStatus(circle.getId(), MembershipStatus.ACTIVE).size();
        boolean hasRoom = circle.getStatus() == CircleStatus.FORMING && activeMemberCount < circle.getMaxMembers();
        if (!hasRoom) {
            return false;
        }
        boolean isCreator = circle.getCreatedBy().getId().equals(candidate.getId());
        if (circle.isGated() && !isCreator) {
            return circleInviteRepository.existsByCircle_IdAndInvitedUser_IdAndRedeemedAtIsNotNull(
                    circle.getId(), candidate.getId());
        }
        return true;
    }

    @Override
    public BigDecimal calculatePotSize(Circle circle) {
        return circle.getContributionAmount().multiply(BigDecimal.valueOf(circle.getMaxMembers()));
    }

    @Override
    public int getTotalRounds(Circle circle) {
        return circle.getMaxMembers();
    }

    @Override
    public BigDecimal calculatePotentialGroupLoss(Circle circle, User member, int payoutPosition) {
        BigDecimal loss = circle.getContributionAmount().multiply(BigDecimal.valueOf(circle.getMaxMembers() - payoutPosition));
        return circle.isGated() ? loss.multiply(GATED_LOSS_DISCOUNT) : loss;
    }
}
