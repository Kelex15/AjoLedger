package com.ajoledger.backend.circle.dto;

import com.ajoledger.backend.circle.Circle;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CircleResponseDTO(
    UUID id,
    String name,
    String description,
    int maxMembers,
    int term,
    BigDecimal contributionAmount,
    int currentRound,
    String status,
    boolean gated,
    Double minReputationScore,
    Instant createdAt
) {

    public static CircleResponseDTO fromCircle(Circle circle) {
        return new CircleResponseDTO(
                circle.getId(),
                circle.getName(),
                circle.getDescription(),
                circle.getMaxMembers(),
                circle.getTerm(),
                circle.getContributionAmount(),
                circle.getCurrentRound(),
                circle.getStatus().name(),
                circle.isGated(),
                circle.getMinReputationScore(),
                circle.getCreatedAt());
    }
}
