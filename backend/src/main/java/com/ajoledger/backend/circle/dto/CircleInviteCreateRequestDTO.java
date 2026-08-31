package com.ajoledger.backend.circle.dto;

import jakarta.validation.constraints.AssertTrue;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CircleInviteCreateRequestDTO {

    private String username;
    private String email;

    @AssertTrue(message = "Exactly one of username or email must be provided")
    public boolean isExactlyOneTarget() {
        return (username != null) ^ (email != null);
    }
}
