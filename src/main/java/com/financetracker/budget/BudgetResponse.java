package com.financetracker.budget;

public record BudgetResponse(Long id, Long categoryId, String category, String currency, long limitMinor) {

    static BudgetResponse from(Budget budget) {
        return new BudgetResponse(budget.getId(), budget.getCategory().getId(), budget.getCategory().getName(),
                budget.getCurrency(), budget.getLimitMinor());
    }
}
