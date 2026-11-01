package com.financetracker.budget;

import java.sql.Types;
import java.time.YearMonth;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads each budget together with what was spent in its category, in one query. */
@Repository
class BudgetSpendingRepository {

    record BudgetSpending(Long budgetId, Long categoryId, String category, String currency, long limitMinor,
            long spentMinor) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    BudgetSpendingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The LEFT JOINs keep budgets with no spending (spent = 0). A budget only counts expenses from the
     * user's accounts in the budget's own currency, so a USD purchase never eats into a RUB budget.
     */
    List<BudgetSpending> findSpending(Long userId, YearMonth month) {
        String sql = """
                SELECT b.id AS budget_id, b.category_id, c.name AS category, b.currency, b.limit_minor,
                       COALESCE(-SUM(t.amount_minor), 0) AS spent_minor
                FROM budgets b
                JOIN categories c ON c.id = b.category_id
                LEFT JOIN accounts a ON a.user_id = b.user_id AND a.currency = b.currency
                LEFT JOIN transactions t ON t.account_id = a.id
                                        AND t.category_id = b.category_id
                                        AND t.amount_minor < 0
                                        AND substr(t.occurred_on, 1, 7) = :month
                WHERE b.user_id = :userId
                GROUP BY b.id, b.category_id, c.name, b.currency, b.limit_minor
                ORDER BY c.name, b.currency
                """;
        return jdbc.query(sql,
                new MapSqlParameterSource()
                        .addValue("userId", userId, Types.BIGINT)
                        .addValue("month", month.toString(), Types.VARCHAR),
                (rs, i) -> new BudgetSpending(rs.getLong("budget_id"), rs.getLong("category_id"),
                        rs.getString("category"), rs.getString("currency"), rs.getLong("limit_minor"),
                        rs.getLong("spent_minor")));
    }
}
