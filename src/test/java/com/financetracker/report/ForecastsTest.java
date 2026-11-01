package com.financetracker.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ForecastsTest {

    @Test
    void movingAverageUsesOnlyTheLastWindow() {
        assertThat(Forecasts.movingAverage(List.of(1_000L, 3_676_965L, 5_118_350L, 4_084_890L), 3))
                .isEqualTo(4_293_402);
    }

    @Test
    void movingAverageOfFewerValuesThanTheWindowUsesAllOfThem() {
        assertThat(Forecasts.movingAverage(List.of(1_000L, 3_000L), 3)).isEqualTo(2_000);
        assertThat(Forecasts.movingAverage(List.of(), 3)).isZero();
    }

    @Test
    void trendContinuesTheStraightLineOneMonthAhead() {
        // slope (4 084 890 - 3 676 965) / 2 = 203 962.5 per month, mean 4 293 401.67 -> next 4 701 326.67
        assertThat(Forecasts.linearTrend(List.of(3_676_965L, 5_118_350L, 4_084_890L))).isEqualTo(4_701_327);
        assertThat(Forecasts.linearTrend(List.of(100L, 200L, 300L, 400L))).isEqualTo(500);
    }

    @Test
    void trendNeedsAtLeastThreeMonths() {
        assertThat(Forecasts.linearTrend(List.of(100L, 200L))).isNull();
        assertThat(Forecasts.linearTrend(List.of())).isNull();
    }

    @Test
    void aFallingTrendNeverPredictsNegativeSpending() {
        assertThat(Forecasts.linearTrend(List.of(200L, 100L, 0L))).isZero();
    }
}
