package com.financetracker.importer;

/** A data row that could not be imported; {@code row} counts data rows from 1, header excluded. */
public record ImportError(int row, String message) {
}
