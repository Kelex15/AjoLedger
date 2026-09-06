package com.ajoledger.backend.account.dto;

import java.util.UUID;

import lombok.Builder;

@Builder
public record AccountRequestDTO(UUID userId) {
}
