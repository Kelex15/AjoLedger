package com.ajoledger.backend.circle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record LienAmountRequestDTO(
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive") BigDecimal amount,

    @NotBlank(message = "Idempotency key is required") String idempotencyKey
) {
}
