package com.ajoledger.backend.circle;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TrustCalculationsTest {

    @Test
    void groupLossIsContributionTimesRoundsAfterPosition() {
        BigDecimal loss = TrustCalculations.calculateGroupLoss(new BigDecimal("100.00"), 5, 2);

        assertThat(loss).isEqualByComparingTo("300.00");
    }

    @Test
    void groupLossIsZeroAtTheLastPosition() {
        BigDecimal loss = TrustCalculations.calculateGroupLoss(new BigDecimal("100.00"), 5, 5);

        assertThat(loss).isEqualByComparingTo("0.00");
    }

    @Test
    void gatedDiscountAppliesOnlyWhenGated() {
        BigDecimal loss = new BigDecimal("300.00");

        assertThat(TrustCalculations.applyGatedDiscount(loss, true, new BigDecimal("0.5")))
                .isEqualByComparingTo("150.00");
        assertThat(TrustCalculations.applyGatedDiscount(loss, false, new BigDecimal("0.5")))
                .isEqualByComparingTo("300.00");
    }

    @Test
    void penalizedFailuresWeightsDefaultsMoreThanWithdrawals() {
        assertThat(TrustCalculations.calculatePenalizedFailures(0, 1, 3)).isEqualTo(3);
        assertThat(TrustCalculations.calculatePenalizedFailures(3, 0, 3)).isEqualTo(3);
        assertThat(TrustCalculations.calculatePenalizedFailures(1, 1, 3)).isEqualTo(4);
    }

    @Test
    void successRateWithNoHistoryIsNeutral() {
        double rate = TrustCalculations.calculateSuccessRate(0, 0, 1);

        assertThat(rate).isEqualTo(0.5);
    }

    @Test
    void successRateFavorsMoreSuccessesOverMoreFailures() {
        double manySuccesses = TrustCalculations.calculateSuccessRate(10, 1, 1);
        double manyFailures = TrustCalculations.calculateSuccessRate(1, 10, 1);

        assertThat(manySuccesses).isGreaterThan(manyFailures);
    }

    @Test
    void lienCoverageRatioIsCappedAtOne() {
        double ratio = TrustCalculations.calculateLienCoverageRatio(500, 100);

        assertThat(ratio).isEqualTo(1.0);
    }

    @Test
    void lienCoverageRatioIsProportionalBelowTheCap() {
        double ratio = TrustCalculations.calculateLienCoverageRatio(50, 100);

        assertThat(ratio).isEqualTo(0.5);
    }

    @Test
    void reputationScoreBlendsSuccessRateAndLienCoverageByWeight() {
        double score = TrustCalculations.calculateReputationScore(0.8, 0.2, 0.75);

        assertThat(score).isCloseTo(0.65, within(1e-9));
    }

    @Test
    void cappedLienShareCoversTheFullNeedWhenUnderTheCap() {
        BigDecimal groupLoss = new BigDecimal("100.00");
        BigDecimal currentLien = new BigDecimal("40.00");
        BigDecimal grossAmount = new BigDecimal("1000.00");

        BigDecimal share = TrustCalculations.calculateCappedLienShare(groupLoss, currentLien, grossAmount, 0.30);

        assertThat(share).isEqualByComparingTo("60.00");
    }

    @Test
    void cappedLienShareIsLimitedByTheCapEvenWhenNeedIsLarger() {
        BigDecimal groupLoss = new BigDecimal("1000.00");
        BigDecimal currentLien = BigDecimal.ZERO;
        BigDecimal grossAmount = new BigDecimal("200.00");

        BigDecimal share = TrustCalculations.calculateCappedLienShare(groupLoss, currentLien, grossAmount, 0.30);

        assertThat(share).isEqualByComparingTo("60.00");
    }

    @Test
    void cappedLienShareIsZeroWhenExistingLienAlreadyCoversTheLoss() {
        BigDecimal groupLoss = new BigDecimal("100.00");
        BigDecimal currentLien = new BigDecimal("150.00");
        BigDecimal grossAmount = new BigDecimal("1000.00");

        BigDecimal share = TrustCalculations.calculateCappedLienShare(groupLoss, currentLien, grossAmount, 0.30);

        assertThat(share).isEqualByComparingTo("0.00");
    }

    @Test
    void cappedAmountReturnsTheFullNeedWhenUnderTheCap() {
        BigDecimal capped = TrustCalculations.calculateCappedAmount(new BigDecimal("20.00"), new BigDecimal("1000.00"), 0.30);

        assertThat(capped).isEqualByComparingTo("20.00");
    }

    @Test
    void cappedAmountIsLimitedByTheCapEvenWhenNeedIsLarger() {
        BigDecimal capped = TrustCalculations.calculateCappedAmount(new BigDecimal("1000.00"), new BigDecimal("200.00"), 0.30);

        assertThat(capped).isEqualByComparingTo("60.00");
    }
}
