package com.financetracker.importer;

import java.time.LocalDate;

/** One successfully parsed CSV row; {@code category} is null when the column is absent or empty. */
public record StatementRow(int row, LocalDate date, long amountMinor, String description, String category) {
}
