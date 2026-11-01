package com.financetracker.report;

import java.util.List;

/** Two deliberately simple forecasts of next month's expenses from a series of monthly totals. */
final class Forecasts {

    /** A line through fewer points says nothing about a trend. */
    static final int MIN_POINTS_FOR_TREND = 3;

    private Forecasts() {
    }

    /** Mean of the last {@code window} values (or of all of them if there are fewer). */
    static long movingAverage(List<Long> values, int window) {
        List<Long> tail = values.subList(Math.max(0, values.size() - window), values.size());
        return Math.round(tail.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    /**
     * Least-squares line through the values (x = 0, 1, 2, ...) extended one step ahead. Expenses cannot
     * be negative, so a steeply falling trend is floored at 0. Null when there are too few points.
     */
    static Long linearTrend(List<Long> values) {
        int n = values.size();
        if (n < MIN_POINTS_FOR_TREND) {
            return null;
        }
        double meanX = (n - 1) / 2.0;
        double meanY = values.stream().mapToLong(Long::longValue).average().orElse(0);
        double covariance = 0;
        double varianceX = 0;
        for (int x = 0; x < n; x++) {
            covariance += (x - meanX) * (values.get(x) - meanY);
            varianceX += (x - meanX) * (x - meanX);
        }
        double slope = covariance / varianceX;
        return Math.max(0, Math.round(meanY + slope * (n - meanX)));
    }
}
