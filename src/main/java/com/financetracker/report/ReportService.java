package com.financetracker.report;

import com.financetracker.account.Account;
import com.financetracker.account.AccountRepository;
import com.financetracker.common.BadRequestException;
import com.financetracker.common.CurrentUser;
import com.financetracker.common.NotFoundException;
import com.financetracker.report.Reports.CategoryChange;
import com.financetracker.report.Reports.CategorySpending;
import com.financetracker.report.Reports.CategorySpendingReport;
import com.financetracker.report.Reports.MerchantReport;
import com.financetracker.report.Reports.MonthComparisonReport;
import com.financetracker.report.Reports.MonthlyReport;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reports;
    private final AccountRepository accounts;
    private final CurrentUser currentUser;

    ReportService(ReportRepository reports, AccountRepository accounts, CurrentUser currentUser) {
        this.reports = reports;
        this.accounts = accounts;
        this.currentUser = currentUser;
    }

    public CategorySpendingReport spendingByCategory(Long accountId, String currency, LocalDate from, LocalDate to) {
        ReportScope scope = scope(accountId, currency, from, to);
        List<CategorySpending> categories = reports.spendingByCategory(scope);
        long total = categories.stream().mapToLong(CategorySpending::totalMinor).sum();
        return new CategorySpendingReport(scope.currency(), total, categories);
    }

    public MonthlyReport monthly(Long accountId, String currency, LocalDate from, LocalDate to) {
        ReportScope scope = scope(accountId, currency, from, to);
        return new MonthlyReport(scope.currency(), reports.monthly(scope));
    }

    public MerchantReport topMerchants(Long accountId, String currency, LocalDate from, LocalDate to, int limit) {
        ReportScope scope = scope(accountId, currency, from, to);
        return new MerchantReport(scope.currency(), reports.topMerchants(scope, limit));
    }

    /** Compares a month with the one before it; the date range filters do not apply here. */
    public MonthComparisonReport compareMonths(Long accountId, String currency, YearMonth month) {
        ReportScope scope = scope(accountId, currency, null, null);
        YearMonth previous = month.minusMonths(1);
        List<CategoryChange> categories = reports.monthComparison(scope, month, previous);
        long current = categories.stream().mapToLong(CategoryChange::currentMinor).sum();
        long before = categories.stream().mapToLong(CategoryChange::previousMinor).sum();
        long delta = current - before;
        Double percent = before > 0 ? Math.round(1000.0 * delta / before) / 10.0 : null;
        return new MonthComparisonReport(scope.currency(), month.toString(), previous.toString(),
                current, before, delta, percent, categories);
    }

    /**
     * Picks the single currency a report is calculated in:
     * the account's own currency, else the requested one, else the user's only currency.
     * Mixing currencies would produce meaningless sums, so an ambiguous request is rejected.
     */
    private ReportScope scope(Long accountId, String currency, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        Long userId = currentUser.id();
        String resolved;
        if (accountId != null) {
            Account account = accounts.findByIdAndUserId(accountId, userId)
                    .orElseThrow(() -> new NotFoundException("Account", accountId));
            if (currency != null && !currency.equals(account.getCurrency())) {
                throw new BadRequestException("Account " + accountId + " is in " + account.getCurrency()
                        + ", not " + currency);
            }
            resolved = account.getCurrency();
        } else if (currency != null) {
            resolved = currency;
        } else {
            List<String> currencies = accounts.findCurrencies(userId);
            if (currencies.size() > 1) {
                throw new BadRequestException("Your accounts use several currencies " + currencies
                        + "; pass accountId or currency to choose one");
            }
            resolved = currencies.isEmpty() ? null : currencies.get(0);
        }
        return new ReportScope(userId, resolved, accountId, from, to);
    }
}
