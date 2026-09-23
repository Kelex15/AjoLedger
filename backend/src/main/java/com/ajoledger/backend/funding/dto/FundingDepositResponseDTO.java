package com.ajoledger.backend.funding.dto;

/** Exactly one field is non-null: transaction when the deposit completed synchronously,
 * checkoutUrl when it's pending external confirmation and the caller should redirect there. */
public record FundingDepositResponseDTO(
    FundingTransactionResponseDTO transaction,
    String checkoutUrl
) {

    public static FundingDepositResponseDTO completed(FundingTransactionResponseDTO transaction) {
        return new FundingDepositResponseDTO(transaction, null);
    }

    public static FundingDepositResponseDTO pendingRedirect(String checkoutUrl) {
        return new FundingDepositResponseDTO(null, checkoutUrl);
    }
}
