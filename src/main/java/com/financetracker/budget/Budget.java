package com.financetracker.budget;

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

@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String currency;

    @Column(name = "limit_minor", nullable = false)
    private long limitMinor;

    protected Budget() {
    }

    public Budget(Long userId, Category category, String currency, long limitMinor) {
        this.userId = userId;
        update(category, currency, limitMinor);
    }

    public void update(Category category, String currency, long limitMinor) {
        this.category = category;
        this.currency = currency;
        this.limitMinor = limitMinor;
    }

    public Long getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public String getCurrency() {
        return currency;
    }

    public long getLimitMinor() {
        return limitMinor;
    }
}
