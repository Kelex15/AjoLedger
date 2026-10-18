package com.ajoledger.backend.circle;

import com.ajoledger.backend.circle.entity.CircleMembership;

/** payoutPosition is a live preview, not the frozen position CircleMembership itself stores. */
public record CircleMemberStanding(CircleMembership membership, double reputationScore, int payoutPosition) {
}
