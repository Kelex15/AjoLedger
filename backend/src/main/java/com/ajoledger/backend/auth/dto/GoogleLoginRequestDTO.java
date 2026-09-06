package com.ajoledger.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequestDTO(
    @NotBlank(message = "idToken is required") String idToken
) {
}
