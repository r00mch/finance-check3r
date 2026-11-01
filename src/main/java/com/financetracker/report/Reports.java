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

    public record CategoryChange(Long categoryId, String category, long currentMinor, long previousMinor,
            long deltaMinor, Double deltaPercent) {
    }

    public record MonthComparisonReport(String currency, String month, String previousMonth,
            long currentTotalMinor, long previousTotalMinor, long deltaMinor, Double deltaPercent,
            List<CategoryChange> categories) {
    }
}
