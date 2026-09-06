package com.ajoledger.backend.auth.dto;

import lombok.Builder;

@Builder
public record AuthResponseDTO(String accessToken, String refreshToken, String tokenType, long expiresIn) {
    public AuthResponseDTO {
        if (tokenType == null) {
            tokenType = "Bearer";
        }
    }
}
