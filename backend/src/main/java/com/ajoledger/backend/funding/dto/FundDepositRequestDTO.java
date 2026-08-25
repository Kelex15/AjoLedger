package com.ajoledger.backend.funding.dto;

import com.ajoledger.backend.account.UserAccountType;
import com.ajoledger.backend.funding.FundingType;

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
public class FundDepositRequestDTO {

    @NotNull(message = "Target account type is required")
    private UserAccountType targetType;

    @NotNull(message = "Funding type is required")
    private FundingType fundingType;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;
}
