package com.ajoledger.backend.circle.dto;

import com.ajoledger.backend.circle.CircleMembership;

import java.time.Instant;
import java.util.UUID;

public record CircleMembershipResponseDTO(
    UUID id,
    UUID circleId,
    UUID userId,
    Integer position,
    String status,
    Instant joinedAt
) {

    public static CircleMembershipResponseDTO fromMembership(CircleMembership membership) {
        return new CircleMembershipResponseDTO(
                membership.getId(),
                membership.getCircle().getId(),
                membership.getUser().getId(),
                membership.getPosition(),
                membership.getStatus().name(),
                membership.getJoinedAt());
    }
}
