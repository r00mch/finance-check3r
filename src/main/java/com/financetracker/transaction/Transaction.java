package com.financetracker.transaction;

import com.financetracker.account.Account;
import com.financetracker.category.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    private String description;

    protected Transaction() {
    }

    public Transaction(Account account, Category category, long amountMinor, LocalDate occurredOn,
            String description) {
        update(account, category, amountMinor, occurredOn, description);
    }

    public void update(Account account, Category category, long amountMinor, LocalDate occurredOn,
            String description) {
        this.account = account;
        this.category = category;
        this.amountMinor = amountMinor;
        this.occurredOn = occurredOn;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public Category getCategory() {
        return category;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public String getDescription() {
        return description;
    }
}
