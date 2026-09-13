package com.ajoledger.backend.circle.dto;

import com.ajoledger.backend.circle.CircleMemberStanding;
import com.ajoledger.backend.circle.CircleMembership;

import java.time.Instant;
import java.util.UUID;

public record CircleMemberResponseDTO(
    UUID userId,
    Integer joinPosition,
    int payoutPosition,
    String status,
    Instant joinedAt,
    double reputationScore
) {

    public static CircleMemberResponseDTO of(CircleMemberStanding standing) {
        CircleMembership membership = standing.membership();
        return new CircleMemberResponseDTO(
                membership.getUser().getId(),
                membership.getPosition(),
                standing.payoutPosition(),
                membership.getStatus().name(),
                membership.getJoinedAt(),
                standing.reputationScore());
    }
}
