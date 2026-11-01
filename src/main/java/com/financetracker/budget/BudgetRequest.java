package com.financetracker.budget;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** {@code limitMinor}: the monthly limit in minor units, e.g. 3000000 = 30 000.00. */
public record BudgetRequest(
        @NotNull Long categoryId,
        @NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO code, e.g. RUB") String currency,
        @NotNull @Positive Long limitMinor) {
}
