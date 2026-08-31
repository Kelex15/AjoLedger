package com.ajoledger.backend.circle.dto;

import com.ajoledger.backend.circle.CircleMembership;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class CircleMembershipResponseDTO {
    private UUID id;
    private UUID circleId;
    private UUID userId;
    private Integer position;
    private String status;
    private LocalDateTime joinedAt;

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
