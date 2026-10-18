package com.ajoledger.backend.ledger;

import java.math.BigDecimal;

/** Where a payout went. The reserve part still belongs to the recipient, it's just held in their lien. */
public record PayoutSplit(BigDecimal savings, BigDecimal reserve) {

    public BigDecimal total() {
        return savings.add(reserve);
    }
}
