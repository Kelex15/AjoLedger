package com.ajoledger.backend.circle.dto;

import com.ajoledger.backend.circle.CircleInvite;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class CircleInviteResponseDTO {
    private UUID id;
    private UUID circleId;
    private String code;

    public static CircleInviteResponseDTO fromInvite(CircleInvite invite) {
        return new CircleInviteResponseDTO(
                invite.getId(),
                invite.getCircle().getId(),
                invite.getCode());
    }
}
