package com.financetracker.budget;

import com.financetracker.budget.BudgetSpendingRepository.BudgetSpending;
import com.financetracker.budget.BudgetStatus.State;
import com.financetracker.category.Category;
import com.financetracker.category.CategoryRepository;
import com.financetracker.common.ConflictException;
import com.financetracker.common.CurrentUser;
import com.financetracker.common.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BudgetService {

    /** A budget turns to WARNING once this share of the limit is used. */
    static final double WARNING_PERCENT = 80.0;

    private final BudgetRepository budgets;
    private final BudgetSpendingRepository spending;
    private final CategoryRepository categories;
    private final CurrentUser currentUser;
    private final Clock clock;

    BudgetService(BudgetRepository budgets, BudgetSpendingRepository spending, CategoryRepository categories,
            CurrentUser currentUser, Clock clock) {
        this.budgets = budgets;
        this.spending = spending;
        this.categories = categories;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public List<BudgetResponse> list() {
        return budgets.findAllByUser(currentUser.id()).stream().map(BudgetResponse::from).toList();
    }

    public BudgetResponse get(Long id) {
        return BudgetResponse.from(find(id));
    }

    @Transactional
    public BudgetResponse create(BudgetRequest request) {
        Category category = ownCategory(request.categoryId());
        if (budgets.existsByUserIdAndCategory_IdAndCurrency(currentUser.id(), category.getId(), request.currency())) {
            throw duplicate(category, request.currency());
        }
        Budget saved = budgets.save(new Budget(currentUser.id(), category, request.currency(), request.limitMinor()));
        return BudgetResponse.from(saved);
    }

    @Transactional
    public BudgetResponse update(Long id, BudgetRequest request) {
        Budget budget = find(id);
        Category category = ownCategory(request.categoryId());
        if (budgets.existsByUserIdAndCategory_IdAndCurrencyAndIdNot(
                currentUser.id(), category.getId(), request.currency(), id)) {
            throw duplicate(category, request.currency());
        }
        budget.update(category, request.currency(), request.limitMinor());
        return BudgetResponse.from(budget);
    }

    @Transactional
    public void delete(Long id) {
        budgets.delete(find(id));
    }

    /** Status of every budget for the given month (default: the current one). */
    public List<BudgetStatus> status(YearMonth month) {
        LocalDate today = LocalDate.now(clock);
        YearMonth target = month != null ? month : YearMonth.from(today);
        return spending.findSpending(currentUser.id(), target).stream()
                .map(s -> toStatus(s, target, today))
                .toList();
    }

    private static BudgetStatus toStatus(BudgetSpending s, YearMonth month, LocalDate today) {
        long remaining = s.limitMinor() - s.spentMinor();
        double usedPercent = Math.round(1000.0 * s.spentMinor() / s.limitMinor()) / 10.0;
        State state = s.spentMinor() > s.limitMinor() ? State.EXCEEDED
                : usedPercent >= WARNING_PERCENT ? State.WARNING : State.OK;
        long projected = project(s.spentMinor(), month, today);
        return new BudgetStatus(s.budgetId(), s.categoryId(), s.category(), s.currency(), month.toString(),
                s.limitMinor(), s.spentMinor(), remaining, usedPercent, state, projected,
                projected > s.limitMinor());
    }

    /**
     * Straight-line pace: spent so far / days elapsed * days in month. Only meaningful for the month in
     * progress; a finished (or not yet started) month projects to what was actually spent.
     */
    static long project(long spentMinor, YearMonth month, LocalDate today) {
        if (!YearMonth.from(today).equals(month)) {
            return spentMinor;
        }
        return Math.round((double) spentMinor * month.lengthOfMonth() / today.getDayOfMonth());
    }

    private Budget find(Long id) {
        return budgets.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Budget", id));
    }

    private Category ownCategory(Long categoryId) {
        return categories.findByIdAndUserId(categoryId, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Category", categoryId));
    }

    private static ConflictException duplicate(Category category, String currency) {
        return new ConflictException("A " + currency + " budget for category '" + category.getName()
                + "' already exists");
    }
}
