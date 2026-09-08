package com.ajoledger.backend.circle;

import java.math.BigDecimal;

/** Pure trust/risk model math (design/v3.md), kept free of entities and repositories so each
 * formula is directly unit-testable. */
public final class TrustCalculations {

    private TrustCalculations() {
    }

    /** A member's remaining contribution exposure across every round after payoutPosition. */
    public static BigDecimal calculateGroupLoss(BigDecimal contributionAmount, int totalRounds, int payoutPosition) {
        return contributionAmount.multiply(BigDecimal.valueOf(totalRounds - payoutPosition));
    }

    public static BigDecimal applyGatedDiscount(BigDecimal loss, boolean gated, BigDecimal gatedLossDiscount) {
        return gated ? loss.multiply(gatedLossDiscount) : loss;
    }

    public static long calculatePenalizedFailures(long withdrawals, long defaults, long defaultPenaltyMultiplier) {
        return withdrawals + defaults * defaultPenaltyMultiplier;
    }

    public static double calculateSuccessRate(long successes, long penalizedFailures, long smoothingPrior) {
        double smoothedSuccesses = successes + smoothingPrior;
        double smoothedFailures = penalizedFailures + smoothingPrior;
        return smoothedSuccesses / (smoothedSuccesses + smoothedFailures);
    }

    public static double calculateLienCoverageRatio(double lienBalance, double worstCaseExposure) {
        return Math.min(1.0, lienBalance / worstCaseExposure);
    }

    public static double calculateReputationScore(double successRate, double lienCoverageRatio, double lienWeight) {
        return lienWeight * successRate + (1 - lienWeight) * lienCoverageRatio;
    }

    public static BigDecimal calculateCappedLienShare(BigDecimal groupLoss, BigDecimal currentLienBalance,
                                                        BigDecimal grossAmount, double capRate) {
        BigDecimal neededTopUp = groupLoss.subtract(currentLienBalance).max(BigDecimal.ZERO);
        BigDecimal cap = grossAmount.multiply(BigDecimal.valueOf(capRate));
        return neededTopUp.min(cap);
    }
}
