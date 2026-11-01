package com.financetracker.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class AnomalyDetectorTest {

    @Test
    void hugePurchaseScoresFarAboveTheThreshold() {
        // peers: mean 1000, standard deviation 50 -> E[x^2] = 1000^2 + 50^2
        double z = AnomalyDetector.zScore(9_000, 1_000, 1_000_000 + 2_500);

        assertThat(z).isCloseTo(160.0, within(1e-6));
    }

    @Test
    void ordinaryAndBelowAverageAmountsDoNotScoreHigh() {
        assertThat(AnomalyDetector.zScore(1_000, 1_000, 1_002_500)).isZero();
        assertThat(AnomalyDetector.zScore(900, 1_000, 1_002_500)).isNegative();
    }

    @Test
    void identicalHistoryDoesNotProduceInfiniteScores() {
        // 25 000 rent every month: standard deviation is 0, the 5 % floor of the mean (1 250) applies
        double z = AnomalyDetector.zScore(30_000, 25_000, 25_000.0 * 25_000);

        assertThat(z).isCloseTo(4.0, within(1e-6));
    }

    @Test
    void zeroSpreadAndZeroMeanScoresZeroInsteadOfDividingByZero() {
        assertThat(AnomalyDetector.zScore(5, 0, 0)).isZero();
    }
}
