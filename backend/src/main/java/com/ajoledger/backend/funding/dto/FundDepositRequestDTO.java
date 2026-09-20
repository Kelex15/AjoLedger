package com.ajoledger.backend.funding.dto;

import com.ajoledger.backend.funding.FundingType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record FundDepositRequestDTO(
    @NotNull(message = "Funding type is required") FundingType fundingType,

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive") BigDecimal amount,

    @NotBlank(message = "Idempotency key is required") String idempotencyKey
) {
}
