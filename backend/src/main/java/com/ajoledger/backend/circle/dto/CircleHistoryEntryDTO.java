package com.ajoledger.backend.circle.dto;

import com.ajoledger.backend.circle.entity.Circle;
import com.ajoledger.backend.circle.entity.CircleMembership;

import java.time.Instant;
import java.util.UUID;

public record CircleHistoryEntryDTO(
    UUID circleId,
    String circleName,
    String membershipStatus,
    String circleStatus,
    Integer position,
    Instant joinedAt
) {

    public static CircleHistoryEntryDTO fromMembership(CircleMembership membership) {
        Circle circle = membership.getCircle();
        return new CircleHistoryEntryDTO(
                circle.getId(),
                circle.getName(),
                membership.getStatus().name(),
                circle.getStatus().name(),
                membership.getPosition(),
                membership.getJoinedAt());
    }
}
