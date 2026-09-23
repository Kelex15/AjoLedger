package com.ajoledger.backend.circle;

public enum MembershipStatus {
    ACTIVE,
    WITHDRAWN,  // exit before receiving payout. Forfeits standing, no waterfall.
    DEFAULTED,  // stopped contributing after receiving payout. Triggers the default waterfall.
    DISSOLVED,  // the circle failed to form or was cancelled. Not the member's doing, refunded in full.
    COMPLETED,  // every round paid out successfully.
    BANNED      // creator-initiated, before lock, made permanent: refunded in full, but blocks rejoining
                // this circle. A plain removal or self-leave before lock deletes the row outright instead,
                // since there's nothing worth tracking there. This is the one pre-lock exit meant to persist.
}
