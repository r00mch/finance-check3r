package com.financetracker.account;

/** {@code balanceMinor} is the sum of all transactions on the account, in minor units. */
public record AccountResponse(Long id, String name, String currency, long balanceMinor) {
}
