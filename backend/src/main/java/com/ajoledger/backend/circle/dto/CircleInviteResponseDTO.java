package com.ajoledger.backend.circle.dto;

import com.ajoledger.backend.circle.entity.CircleInvite;

import java.util.UUID;

public record CircleInviteResponseDTO(UUID id, UUID circleId, String code) {

    public static CircleInviteResponseDTO fromInvite(CircleInvite invite) {
        return new CircleInviteResponseDTO(
                invite.getId(),
                invite.getCircle().getId(),
                invite.getCode());
    }
}
