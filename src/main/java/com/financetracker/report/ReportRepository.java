package com.financetracker.report;

import com.financetracker.report.Reports.CategoryChange;
import com.financetracker.report.Reports.CategorySpending;
import com.financetracker.report.Reports.MerchantSpending;
import com.financetracker.report.Reports.MonthlySummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.YearMonth;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Analytics are written as plain SQL on purpose: grouping, window functions and CTEs are done by the
 * database in one pass, instead of loading every transaction into Java and looping.
 *
 * <p>Conventions: {@code amount_minor < 0} is an expense, {@code > 0} income; {@code occurred_on} is ISO
 * text, so {@code substr(occurred_on, 1, 7)} is the month ({@code 2025-03}) and ranges compare as strings.
 */
@Repository
class ReportRepository {

    /** Shared WHERE fragment: whose data, which currency, and the optional account/date filters. */
    private static final String SCOPE = """
            a.user_id = :userId
            AND a.currency = :currency
            AND (:accountId IS NULL OR a.id = :accountId)
            AND (:from IS NULL OR t.occurred_on >= :from)
            AND (:to IS NULL OR t.occurred_on <= :to)""";

    private final NamedParameterJdbcTemplate jdbc;

    ReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Expenses per category with each category's share of the total. The share uses a window function:
     * {@code SUM(total) OVER ()} is the grand total, visible on every row without a second query.
     */
    List<CategorySpending> spendingByCategory(ReportScope scope) {
        String sql = """
                WITH spend AS (
                    SELECT c.id                              AS category_id,
                           COALESCE(c.name, 'Uncategorized') AS category,
                           -SUM(t.amount_minor)              AS total_minor,
                           COUNT(*)                          AS transactions
                    FROM transactions t
                    JOIN accounts a        ON a.id = t.account_id
                    LEFT JOIN categories c ON c.id = t.category_id
                    WHERE t.amount_minor < 0 AND %s
                    GROUP BY c.id, c.name
                )
                SELECT category_id, category, total_minor, transactions,
                       ROUND(100.0 * total_minor / SUM(total_minor) OVER (), 1) AS share_percent
                FROM spend
                ORDER BY total_minor DESC, category
                """.formatted(SCOPE);
        return jdbc.query(sql, params(scope), (rs, i) -> new CategorySpending(
                nullableLong(rs, "category_id"), rs.getString("category"), rs.getLong("total_minor"),
                rs.getInt("transactions"), rs.getDouble("share_percent")));
    }

    /**
     * One row per calendar month between the first and last month that has data, <b>including months
     * with no transactions</b> (a recursive CTE generates the month series). Without that, LAG() would
     * treat "January, then April" as consecutive months and the moving average would skip the gap.
     *
     * <p>Window functions over the monthly rows: running net (SUM OVER), previous month (LAG) and a
     * 3-month moving average (AVG over the current row and the two before it).
     */
    List<MonthlySummary> monthly(ReportScope scope) {
        String sql = """
                WITH RECURSIVE
                filtered AS (
                    SELECT t.occurred_on, t.amount_minor
                    FROM transactions t
                    JOIN accounts a ON a.id = t.account_id
                    WHERE %s
                ),
                bounds AS (
                    SELECT MIN(substr(occurred_on, 1, 7)) AS first_month,
                           MAX(substr(occurred_on, 1, 7)) AS last_month
                    FROM filtered
                ),
                months(month) AS (
                    SELECT first_month FROM bounds WHERE first_month IS NOT NULL
                    UNION ALL
                    SELECT strftime('%%Y-%%m', date(months.month || '-01', '+1 month'))
                    FROM months, bounds
                    WHERE months.month < bounds.last_month
                ),
                monthly AS (
                    SELECT m.month,
                           COALESCE(SUM(CASE WHEN f.amount_minor > 0 THEN f.amount_minor END), 0)  AS income_minor,
                           COALESCE(-SUM(CASE WHEN f.amount_minor < 0 THEN f.amount_minor END), 0) AS expenses_minor
                    FROM months m
                    LEFT JOIN filtered f ON substr(f.occurred_on, 1, 7) = m.month
                    GROUP BY m.month
                )
                SELECT month, income_minor, expenses_minor,
                       income_minor - expenses_minor                                        AS net_minor,
                       SUM(income_minor - expenses_minor) OVER (ORDER BY month)             AS cumulative_net_minor,
                       LAG(expenses_minor) OVER (ORDER BY month)                            AS prev_expenses_minor,
                       AVG(expenses_minor) OVER (ORDER BY month ROWS BETWEEN 2 PRECEDING AND CURRENT ROW)
                                                                                            AS moving_avg_minor
                FROM monthly
                ORDER BY month
                """.formatted(SCOPE);
        return jdbc.query(sql, params(scope), (rs, i) -> {
            long income = rs.getLong("income_minor");
            long expenses = rs.getLong("expenses_minor");
            long net = rs.getLong("net_minor");
            long previous = rs.getLong("prev_expenses_minor"); // 0 for the first month (NULL)
            return new MonthlySummary(rs.getString("month"), income, expenses, net,
                    rs.getLong("cumulative_net_minor"),
                    percent(expenses - previous, previous),
                    Math.round(rs.getDouble("moving_avg_minor")),
                    percent(net, income));
        });
    }

    /** Biggest spending targets: transactions with the same description are summed up. */
    List<MerchantSpending> topMerchants(ReportScope scope, int limit) {
        String sql = """
                SELECT t.description                              AS merchant,
                       COUNT(*)                                   AS transactions,
                       -SUM(t.amount_minor)                       AS total_minor,
                       CAST(ROUND(-AVG(t.amount_minor)) AS INTEGER) AS average_minor
                FROM transactions t
                JOIN accounts a ON a.id = t.account_id
                WHERE t.amount_minor < 0 AND t.description IS NOT NULL AND %s
                GROUP BY t.description
                ORDER BY total_minor DESC, merchant
                LIMIT :limit
                """.formatted(SCOPE);
        return jdbc.query(sql, params(scope).addValue("limit", limit, Types.INTEGER),
                (rs, i) -> new MerchantSpending(rs.getString("merchant"), rs.getInt("transactions"),
                        rs.getLong("total_minor"), rs.getLong("average_minor")));
    }

    /**
     * Expenses per category in two months side by side. Conditional aggregation ({@code SUM(CASE ...)})
     * computes both months in one scan; categories present in only one month still appear (other side 0).
     */
    List<CategoryChange> monthComparison(ReportScope scope, YearMonth current, YearMonth previous) {
        String sql = """
                SELECT c.id                              AS category_id,
                       COALESCE(c.name, 'Uncategorized') AS category,
                       COALESCE(-SUM(CASE WHEN substr(t.occurred_on, 1, 7) = :current  THEN t.amount_minor END), 0) AS current_minor,
                       COALESCE(-SUM(CASE WHEN substr(t.occurred_on, 1, 7) = :previous THEN t.amount_minor END), 0) AS previous_minor
                FROM transactions t
                JOIN accounts a        ON a.id = t.account_id
                LEFT JOIN categories c ON c.id = t.category_id
                WHERE t.amount_minor < 0
                  AND substr(t.occurred_on, 1, 7) IN (:current, :previous)
                  AND %s
                GROUP BY c.id, c.name
                ORDER BY current_minor DESC, previous_minor DESC, category
                """.formatted(SCOPE);
        MapSqlParameterSource params = params(scope.withoutDates())
                .addValue("current", current.toString(), Types.VARCHAR)
                .addValue("previous", previous.toString(), Types.VARCHAR);
        return jdbc.query(sql, params, (rs, i) -> {
            long currentMinor = rs.getLong("current_minor");
            long previousMinor = rs.getLong("previous_minor");
            return new CategoryChange(nullableLong(rs, "category_id"), rs.getString("category"),
                    currentMinor, previousMinor, currentMinor - previousMinor,
                    percent(currentMinor - previousMinor, previousMinor));
        });
    }

    // ---- helpers --------------------------------------------------------------------------

    /**
     * Types are given explicitly: SQLite's driver cannot describe parameters, and a null needs a type
     * for {@code :accountId IS NULL}. Dates are bound as ISO text, matching how they are stored.
     */
    private static MapSqlParameterSource params(ReportScope scope) {
        return new MapSqlParameterSource()
                .addValue("userId", scope.userId(), Types.BIGINT)
                .addValue("currency", scope.currency(), Types.VARCHAR)
                .addValue("accountId", scope.accountId(), Types.BIGINT)
                .addValue("from", scope.from() == null ? null : scope.from().toString(), Types.VARCHAR)
                .addValue("to", scope.to() == null ? null : scope.to().toString(), Types.VARCHAR);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    /** {@code part / base} in percent, one decimal; null when there is no base to compare with. */
    private static Double percent(long part, long base) {
        return base > 0 ? Math.round(1000.0 * part / base) / 10.0 : null;
    }
}
