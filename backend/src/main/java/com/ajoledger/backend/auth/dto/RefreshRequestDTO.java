package com.ajoledger.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequestDTO(
    @NotBlank(message = "refreshToken is required") String refreshToken
) {
}
