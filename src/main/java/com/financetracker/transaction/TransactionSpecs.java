package com.financetracker.transaction;

import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

/** Building blocks for the optional filters; a null argument means "do not filter". */
final class TransactionSpecs {

    private TransactionSpecs() {
    }

    static Specification<Transaction> ownedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("account").get("userId"), userId);
    }

    static Specification<Transaction> inAccount(Long accountId) {
        return accountId == null ? null
                : (root, query, cb) -> cb.equal(root.get("account").get("id"), accountId);
    }

    static Specification<Transaction> inCategory(Long categoryId) {
        return categoryId == null ? null
                : (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    static Specification<Transaction> from(LocalDate from) {
        return from == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredOn"), from);
    }

    static Specification<Transaction> to(LocalDate to) {
        return to == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("occurredOn"), to);
    }
}
