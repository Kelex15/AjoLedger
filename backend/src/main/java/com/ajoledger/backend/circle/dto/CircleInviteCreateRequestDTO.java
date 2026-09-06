package com.ajoledger.backend.circle.dto;

import jakarta.validation.constraints.AssertTrue;

public record CircleInviteCreateRequestDTO(String username, String email) {

    @AssertTrue(message = "Exactly one of username or email must be provided")
    public boolean isExactlyOneTarget() {
        return (username != null) ^ (email != null);
    }
}
