package com.financetracker.report;

import com.financetracker.report.Reports.AnomalyReport;
import com.financetracker.report.Reports.CategorySpendingReport;
import com.financetracker.report.Reports.ForecastReport;
import com.financetracker.report.Reports.MerchantReport;
import com.financetracker.report.Reports.MonthComparisonReport;
import com.financetracker.report.Reports.MonthlyReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every report covers all of the user's accounts unless {@code accountId} is given, and always one
 * currency: pass {@code currency} (or {@code accountId}) if the user has accounts in several.
 */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports")
public class ReportController {

    private static final String ISO_CURRENCY = "[A-Z]{3}";

    private final ReportService service;

    ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/spending-by-category")
    @Operation(summary = "Expenses per category with each category's share of the total")
    CategorySpendingReport spendingByCategory(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @Pattern(regexp = ISO_CURRENCY) String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.spendingByCategory(accountId, currency, from, to);
    }

    @GetMapping("/monthly")
    @Operation(summary = "Income, expenses and net per month, with running net, month-over-month change "
            + "and a 3-month moving average of expenses (months without transactions included as zeros)")
    MonthlyReport monthly(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @Pattern(regexp = ISO_CURRENCY) String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.monthly(accountId, currency, from, to);
    }

    @GetMapping("/top-merchants")
    @Operation(summary = "Where the money goes: expenses grouped by description, biggest first")
    MerchantReport topMerchants(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @Pattern(regexp = ISO_CURRENCY) String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return service.topMerchants(accountId, currency, from, to, limit);
    }

    @GetMapping("/anomalies")
    @Operation(summary = "Unusually large expenses compared with the rest of their category (z-score)",
            description = "A category needs at least 5 expenses. threshold = how many standard deviations "
                    + "above the category's average an expense must be (1 to 10, default 2).")
    AnomalyReport anomalies(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @Pattern(regexp = ISO_CURRENCY) String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "2.0") double threshold,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return service.anomalies(accountId, currency, from, to, threshold, limit);
    }

    @GetMapping("/forecast")
    @Operation(summary = "Forecast of next month's expenses: 3-month moving average and linear trend")
    ForecastReport forecast(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @Pattern(regexp = ISO_CURRENCY) String currency) {
        return service.forecast(accountId, currency);
    }

    @GetMapping("/month-comparison")
    @Operation(summary = "Expenses per category in a month versus the month before it")
    MonthComparisonReport monthComparison(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @Pattern(regexp = ISO_CURRENCY) String currency) {
        return service.compareMonths(accountId, currency, month);
    }
}
