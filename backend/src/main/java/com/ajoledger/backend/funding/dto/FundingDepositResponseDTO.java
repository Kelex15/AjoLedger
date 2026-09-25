package com.ajoledger.backend.funding.dto;

public record FundingDepositResponseDTO(String checkoutUrl) {

    public static FundingDepositResponseDTO pendingRedirect(String checkoutUrl) {
        return new FundingDepositResponseDTO(checkoutUrl);
    }
}
