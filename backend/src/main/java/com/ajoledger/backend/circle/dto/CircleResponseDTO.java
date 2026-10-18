package com.ajoledger.backend.circle.dto;

import com.ajoledger.backend.account.Currency;
import com.ajoledger.backend.circle.entity.Circle;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CircleResponseDTO(
    UUID id,
    String name,
    String description,
    int maxMembers,
    int totalRounds,
    int term,
    BigDecimal contributionAmount,
    BigDecimal collateral,
    Currency currency,
    int currentRound,
    String status,
    boolean gated,
    Double minReputationScore,
    UUID createdByUserId,
    Instant createdAt
) {

    public static CircleResponseDTO fromCircle(Circle circle, BigDecimal collateral, Currency currency, int totalRounds) {
        return new CircleResponseDTO(
                circle.getId(),
                circle.getName(),
                circle.getDescription(),
                circle.getMaxMembers(),
                totalRounds,
                circle.getTerm(),
                circle.getContributionAmount(),
                collateral,
                currency,
                circle.getCurrentRound(),
                circle.getStatus().name(),
                circle.isGated(),
                circle.getMinReputationScore(),
                circle.getCreatedBy().getId(),
                circle.getCreatedAt());
    }
}
