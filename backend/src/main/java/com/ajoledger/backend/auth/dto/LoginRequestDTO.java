package com.ajoledger.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
    @NotBlank(message = "Email is required") String email,
    @NotBlank(message = "Password is required") String password
) {
}
