package com.ajoledger.backend.ledger.dto;

import com.ajoledger.backend.account.UserAccountType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequestDTO(
    @NotNull(message = "Source account type is required") UserAccountType fromType,

    @NotBlank(message = "Recipient username is required") String toUsername,

    @NotNull(message = "Destination account type is required") UserAccountType toType,

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive") BigDecimal amount,

    @NotBlank(message = "Idempotency key is required") String idempotencyKey
) {
}
