package com.ajoledger.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

import com.ajoledger.backend.auth.validation.StrongPassword;

public record SignupRequestDTO(
    @NotBlank(message = "Username is required") String username,
    @NotBlank(message = "FirstName is required") String firstName,
    @NotBlank(message = "LastName is required") String lastName,
    @NotBlank(message = "Email is required") String email,
    @NotBlank(message = "Password is required") @StrongPassword String password
) {
}
