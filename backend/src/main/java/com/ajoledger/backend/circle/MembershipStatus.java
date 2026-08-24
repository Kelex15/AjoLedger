package com.ajoledger.backend.circle;

public enum MembershipStatus {
    ACTIVE,
    WITHDRAWN, // exit before receiving payout — forfeits standing, no waterfall
    DEFAULTED  // stopped contributing after receiving payout — triggers the default waterfall
}
