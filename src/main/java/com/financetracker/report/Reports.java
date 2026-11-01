package com.financetracker.report;

import java.util.List;

/**
 * Response models of the analytics endpoints. All amounts are positive minor units (kopecks) unless a
 * field says otherwise; "expenses" are reported as positive numbers. Percentages have one decimal and
 * are null when the base value is zero (no meaningful ratio).
 */
public final class Reports {

    private Reports() {
    }

    public record CategorySpending(Long categoryId, String category, long totalMinor, int transactions,
            double sharePercent) {
    }

    public record CategorySpendingReport(String currency, long totalMinor, List<CategorySpending> categories) {
    }

    /**
     * @param cumulativeNetMinor        running sum of net since the first month of the period (may be negative)
     * @param expensesChangePercent     change of expenses vs the previous month
     * @param expensesMovingAvg3mMinor  average expenses of this and up to two preceding months
     * @param savingsRatePercent        net / income
     */
    public record MonthlySummary(String month, long incomeMinor, long expensesMinor, long netMinor,
            long cumulativeNetMinor, Double expensesChangePercent, long expensesMovingAvg3mMinor,
            Double savingsRatePercent) {
    }

    public record MonthlyReport(String currency, List<MonthlySummary> months) {
    }

    public record MerchantSpending(String merchant, int transactions, long totalMinor, long averageMinor) {
    }

    public record MerchantReport(String currency, List<MerchantSpending> merchants) {
    }

    /**
     * @param categoryAverageMinor average of the category's <i>other</i> expenses
     * @param zScore               standard deviations above that average
     */
    public record Anomaly(Long transactionId, java.time.LocalDate date, String description, String category,
            long amountMinor, long categoryAverageMinor, double zScore) {
    }

    public record AnomalyReport(String currency, double threshold, List<Anomaly> anomalies) {
    }

    public record MonthExpense(String month, long expensesMinor) {
    }

    /**
     * @param history              the months the forecast is based on (up to 6, oldest first)
     * @param movingAverageMinor   average of the last 3 months
     * @param trendMinor           straight-line trend continued one month; null with fewer than 3 months
     */
    public record ForecastReport(String currency, String lastMonth, String forecastMonth,
            List<MonthExpense> history, Long movingAverageMinor, Long trendMinor) {
    }

    public record CategoryChange(Long categoryId, String category, long currentMinor, long previousMinor,
            long deltaMinor, Double deltaPercent) {
    }

    public record MonthComparisonReport(String currency, String month, String previousMonth,
            long currentTotalMinor, long previousTotalMinor, long deltaMinor, Double deltaPercent,
            List<CategoryChange> categories) {
    }
}
