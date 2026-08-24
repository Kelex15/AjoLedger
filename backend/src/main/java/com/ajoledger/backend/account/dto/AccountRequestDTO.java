package com.ajoledger.backend.account.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Builder;

@Builder
@Getter
@AllArgsConstructor
public class AccountRequestDTO {
    public UUID userId;
}
