package com.ajoledger.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GoogleLoginRequestDTO {

    @NotBlank(message = "idToken is required")
    private String idToken;
}
