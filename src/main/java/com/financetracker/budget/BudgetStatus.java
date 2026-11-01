package com.financetracker.budget;

/**
 * How one budget stands in a given month.
 *
 * @param remainingMinor    limit minus spent; negative once the limit is exceeded
 * @param usedPercent       spent / limit, one decimal
 * @param state             OK, WARNING (80 % or more used) or EXCEEDED (over the limit)
 * @param projectedMinor    expected spending by the end of the month at the current daily pace
 *                          (for a past month this is simply what was spent)
 * @param projectedToExceed whether that projection is over the limit
 */
public record BudgetStatus(Long budgetId, Long categoryId, String category, String currency, String month,
        long limitMinor, long spentMinor, long remainingMinor, double usedPercent, State state,
        long projectedMinor, boolean projectedToExceed) {

    public enum State { OK, WARNING, EXCEEDED }
}
