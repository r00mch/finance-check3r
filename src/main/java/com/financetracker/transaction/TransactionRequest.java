package com.financetracker.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** {@code amountMinor}: minor units, expense negative (-15050 = spent 150.50), income positive. */
public record TransactionRequest(
        @NotNull Long accountId,
        Long categoryId,
        @NotNull Long amountMinor,
        @NotNull LocalDate occurredOn,
        @Size(max = 500) String description) {

    @JsonIgnore
    @AssertTrue(message = "amountMinor must not be zero")
    public boolean isAmountNonZero() {
        return amountMinor == null || amountMinor != 0;
    }
}
