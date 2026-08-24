package com.ajoledger.backend.transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record Transaction(
    UUID id,
    BigDecimal amount,
    String description,
    TransactionType transactionType,
    LocalDateTime createdAt
) {

}
