package com.financetracker.transaction;

import java.time.LocalDate;

public record TransactionResponse(
        Long id,
        Long accountId,
        Long categoryId,
        long amountMinor,
        LocalDate occurredOn,
        String description) {

    static TransactionResponse from(Transaction t) {
        // getId() on a lazy association does not trigger an extra query
        Long categoryId = t.getCategory() == null ? null : t.getCategory().getId();
        return new TransactionResponse(t.getId(), t.getAccount().getId(), categoryId,
                t.getAmountMinor(), t.getOccurredOn(), t.getDescription());
    }
}
