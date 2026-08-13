package com.ajoledger.backend.circle.policy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import com.ajoledger.backend.circle.Circle;
import com.ajoledger.backend.circle.CircleStatus;
import com.ajoledger.backend.circle.MembershipStatus;
import com.ajoledger.backend.user.User;

@Component
public class FixedSizeCircleSizingPolicy implements CircleSizingPolicy {

    @Override
    public boolean canMemberJoin(Circle circle, User candidate) {
        long activeMemberCount = circle.getMemberships().stream()
                .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
                .count();
        return circle.getStatus() == CircleStatus.FORMING && activeMemberCount < circle.getMaxMembers();
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
        return circle.getContributionAmount().multiply(BigDecimal.valueOf(circle.getMaxMembers() - 1));
    }
}
