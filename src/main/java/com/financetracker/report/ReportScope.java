package com.financetracker.report;

import java.time.LocalDate;

/**
 * What a report is computed over. Money in different currencies cannot be added up, so every report is
 * for exactly one {@code currency} (null only when the user has no accounts at all, which yields empty
 * results). {@code accountId}, {@code from} and {@code to} are optional narrowing filters.
 */
record ReportScope(Long userId, String currency, Long accountId, LocalDate from, LocalDate to) {

    ReportScope withoutDates() {
        return new ReportScope(userId, currency, accountId, null, null);
    }
}
