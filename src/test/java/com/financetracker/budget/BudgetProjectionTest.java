package com.financetracker.budget;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class BudgetProjectionTest {

    private static final YearMonth FEBRUARY = YearMonth.of(2025, 2);

    @Test
    void monthInProgressProjectsAtTheCurrentDailyPace() {
        // 14 of 28 days gone and 7 275.50 spent -> on pace for 14 551.00
        assertThat(BudgetService.project(727_550, FEBRUARY, LocalDate.of(2025, 2, 14))).isEqualTo(1_455_100);
    }

    @Test
    void lastDayOfTheMonthProjectsToWhatWasSpent() {
        assertThat(BudgetService.project(727_550, FEBRUARY, LocalDate.of(2025, 2, 28))).isEqualTo(727_550);
    }

    @Test
    void finishedMonthIsNotExtrapolated() {
        assertThat(BudgetService.project(727_550, FEBRUARY, LocalDate.of(2025, 3, 5))).isEqualTo(727_550);
    }

    @Test
    void futureMonthIsNotExtrapolated() {
        assertThat(BudgetService.project(0, FEBRUARY, LocalDate.of(2025, 1, 10))).isZero();
    }
}
