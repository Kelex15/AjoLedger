package com.ajoledger.backend.circle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class CircleCreateRequestDTO {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Max members is required")
    @Positive(message = "Max members must be positive")
    private Integer maxMembers;

    @NotNull(message = "Term is required")
    @Positive(message = "Term must be positive")
    private Integer term;

    @NotNull(message = "Contribution amount is required")
    @Positive(message = "Contribution amount must be positive")
    private BigDecimal contributionAmount;

    private boolean gated;
}
