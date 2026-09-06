package com.ajoledger.backend.circle.dto;

import com.ajoledger.backend.circle.Circle;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class CircleResponseDTO {
    private UUID id;
    private String name;
    private String description;
    private int maxMembers;
    private int term;
    private BigDecimal contributionAmount;
    private int currentRound;
    private String status;
    private boolean gated;
    private Instant createdAt;

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
                circle.getCreatedAt());
    }
}
