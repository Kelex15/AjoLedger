package com.ajoledger.backend.circle.policy;

import java.math.BigDecimal;

import com.ajoledger.backend.circle.Circle;
import com.ajoledger.backend.user.User;

public interface CircleSizingPolicy {
    boolean canMemberJoin(Circle circle, User candidate);
    BigDecimal calculatePotSize(Circle circle);
    int getTotalRounds(Circle circle);
    BigDecimal calculatePotentialGroupLoss(Circle circle, User member, int payoutPosition);
}
