package com.ajoledger.backend.circle.policy;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import com.ajoledger.backend.circle.Circle;
import com.ajoledger.backend.circle.CircleMembership;
import com.ajoledger.backend.circle.CircleStatus;
import com.ajoledger.backend.circle.MembershipStatus;
import com.ajoledger.backend.user.User;

@Component
public class FixedSizeCircleSizingPolicy implements CircleSizingPolicy {

    @Override
    @SuppressWarnings("null")
    public boolean canMemberJoin(Circle circle, User candidate) {
        long activeMemberCount = circle.getMemberships().stream()
                .map(CircleMembership::getStatus)
                .filter(MembershipStatus.ACTIVE::equals)
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
