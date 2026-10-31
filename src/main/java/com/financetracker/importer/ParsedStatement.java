package com.financetracker.importer;

import java.util.List;

/**
 * @param errors details of the first failed rows (capped); {@code failed} is the true total
 */
public record ParsedStatement(List<StatementRow> rows, List<ImportError> errors, int failed) {

    public int rowsRead() {
        return rows.size() + failed;
    }
}
