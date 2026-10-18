package com.ajoledger.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequestDTO(
    @NotBlank(message = "Email is required") String email
) {
}
