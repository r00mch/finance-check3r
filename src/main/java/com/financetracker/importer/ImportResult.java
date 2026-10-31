package com.financetracker.importer;

import java.util.List;

/**
 * @param rowsRead   data rows found in the file
 * @param imported   new transactions created
 * @param duplicates rows skipped because an earlier import already contains them
 * @param failed     rows that could not be parsed (details in {@code errors}, capped at 50)
 */
public record ImportResult(int rowsRead, int imported, int duplicates, int failed, List<ImportError> errors) {
}
