package com.ajoledger.backend.transaction.dto;

import com.ajoledger.backend.account.UserAccountType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class TransferRequestDTO {

    @NotNull(message = "Source account type is required")
    private UserAccountType fromType;

    @NotNull(message = "Recipient user id is required")
    private UUID toUserId;

    @NotNull(message = "Destination account type is required")
    private UserAccountType toType;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;
}
