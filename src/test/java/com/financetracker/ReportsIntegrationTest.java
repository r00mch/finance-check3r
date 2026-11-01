package com.financetracker;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Reports are checked against samples/statement-sample.csv, whose totals were worked out by hand:
 * income 3 x 85 000.00, expenses 36 769.65 (Jan) + 51 183.50 (Feb) + 40 848.90 (Mar) = 128 802.05.
 * Every test creates its own user, so sums never mix between tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportsIntegrationTest {

    private static final Path TEST_DB = Path.of("target", "test-db", "reports.db");
    private static final Path SAMPLE = Path.of("samples", "statement-sample.csv");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws IOException {
        Files.createDirectories(TEST_DB.getParent());
        Files.deleteIfExists(TEST_DB);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + TEST_DB + "?foreign_keys=on");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    // ---- spending by category -------------------------------------------------------------

    @Test
    void spendingByCategoryRanksCategoriesAndComputesShares() throws Exception {
        long user = newUser();
        importSample(user, createAccount(user, "RUB"));

        mvc.perform(get("/api/v1/reports/spending-by-category").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.totalMinor").value(12_880_205))
                .andExpect(jsonPath("$.categories.length()").value(8))
                .andExpect(jsonPath("$.categories[0].category").value("Жильё"))
                .andExpect(jsonPath("$.categories[0].totalMinor").value(7_500_000))
                .andExpect(jsonPath("$.categories[0].transactions").value(3))
                .andExpect(jsonPath("$.categories[0].sharePercent").value(58.2))
                .andExpect(jsonPath("$.categories[1].category").value("Продукты"))
                .andExpect(jsonPath("$.categories[1].totalMinor").value(1_893_805));
    }

    @Test
    void spendingByCategoryRespectsTheDateRange() throws Exception {
        long user = newUser();
        importSample(user, createAccount(user, "RUB"));

        mvc.perform(get("/api/v1/reports/spending-by-category").with(as(user))
                        .param("from", "2025-02-01").param("to", "2025-02-28"))
                .andExpect(jsonPath("$.totalMinor").value(5_118_350));
    }

    @Test
    void transactionsWithoutCategoryAreGroupedAsUncategorized() throws Exception {
        long user = newUser();
        long account = createAccount(user, "RUB");
        addTransaction(user, account, -700, "2025-01-10", "Cash");

        mvc.perform(get("/api/v1/reports/spending-by-category").with(as(user)))
                .andExpect(jsonPath("$.categories[0].category").value("Uncategorized"))
                .andExpect(jsonPath("$.categories[0].categoryId").value(nullValue()))
                .andExpect(jsonPath("$.categories[0].sharePercent").value(100.0));
    }

    // ---- monthly ---------------------------------------------------------------------------

    @Test
    void monthlyReportHasRunningNetChangeAndMovingAverage() throws Exception {
        long user = newUser();
        importSample(user, createAccount(user, "RUB"));

        mvc.perform(get("/api/v1/reports/monthly").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months.length()").value(3))
                // January: no previous month, so no change percentage
                .andExpect(jsonPath("$.months[0].month").value("2025-01"))
                .andExpect(jsonPath("$.months[0].incomeMinor").value(8_500_000))
                .andExpect(jsonPath("$.months[0].expensesMinor").value(3_676_965))
                .andExpect(jsonPath("$.months[0].netMinor").value(4_823_035))
                .andExpect(jsonPath("$.months[0].cumulativeNetMinor").value(4_823_035))
                .andExpect(jsonPath("$.months[0].expensesChangePercent").value(nullValue()))
                .andExpect(jsonPath("$.months[0].savingsRatePercent").value(56.7))
                // February: expenses up 39.2 % on January
                .andExpect(jsonPath("$.months[1].expensesMinor").value(5_118_350))
                .andExpect(jsonPath("$.months[1].expensesChangePercent").value(39.2))
                // March: down 20.2 %, moving average of all three months, running net = account balance
                .andExpect(jsonPath("$.months[2].expensesChangePercent").value(-20.2))
                .andExpect(jsonPath("$.months[2].expensesMovingAvg3mMinor").value(4_293_402))
                .andExpect(jsonPath("$.months[2].cumulativeNetMinor").value(12_619_795));
    }

    @Test
    void monthsWithoutTransactionsAppearAsZeroSoTrendsStayHonest() throws Exception {
        long user = newUser();
        long account = createAccount(user, "RUB");
        addTransaction(user, account, 500_000, "2025-01-01", "Salary");
        addTransaction(user, account, -1_000, "2025-01-10", "Lunch");
        addTransaction(user, account, -2_000, "2025-04-10", "Dinner");

        mvc.perform(get("/api/v1/reports/monthly").with(as(user)))
                .andExpect(jsonPath("$.months.length()").value(4))
                .andExpect(jsonPath("$.months[*].month").value(contains("2025-01", "2025-02", "2025-03", "2025-04")))
                .andExpect(jsonPath("$.months[1].expensesMinor").value(0))
                .andExpect(jsonPath("$.months[1].expensesChangePercent").value(-100.0))
                // April follows two empty months: there is nothing to compare with
                .andExpect(jsonPath("$.months[3].expensesChangePercent").value(nullValue()))
                // (0 + 0 + 2000) / 3 = 666.67 -> 667
                .andExpect(jsonPath("$.months[3].expensesMovingAvg3mMinor").value(667));
    }

    // ---- top merchants ---------------------------------------------------------------------

    @Test
    void topMerchantsSumsRepeatedPurchases() throws Exception {
        long user = newUser();
        importSample(user, createAccount(user, "RUB"));

        mvc.perform(get("/api/v1/reports/top-merchants").with(as(user)).param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchants.length()").value(3))
                .andExpect(jsonPath("$.merchants[0].merchant").value("Аренда квартиры"))
                .andExpect(jsonPath("$.merchants[0].transactions").value(3))
                .andExpect(jsonPath("$.merchants[0].totalMinor").value(7_500_000))
                .andExpect(jsonPath("$.merchants[0].averageMinor").value(2_500_000))
                .andExpect(jsonPath("$.merchants[1].merchant").value("Куртка"))
                .andExpect(jsonPath("$.merchants[2].merchant").value("Подарок"));
    }

    // ---- month comparison ------------------------------------------------------------------

    @Test
    void monthComparisonShowsPerCategoryChange() throws Exception {
        long user = newUser();
        importSample(user, createAccount(user, "RUB"));

        mvc.perform(get("/api/v1/reports/month-comparison").with(as(user)).param("month", "2025-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2025-02"))
                .andExpect(jsonPath("$.previousMonth").value("2025-01"))
                .andExpect(jsonPath("$.currentTotalMinor").value(5_118_350))
                .andExpect(jsonPath("$.previousTotalMinor").value(3_676_965))
                .andExpect(jsonPath("$.deltaMinor").value(1_441_385))
                .andExpect(jsonPath("$.deltaPercent").value(39.2))
                // bought only in February: no base to compute a percentage from
                .andExpect(jsonPath("$.categories[?(@.category == 'Одежда')].currentMinor").value(contains(1_299_000)))
                .andExpect(jsonPath("$.categories[?(@.category == 'Одежда')].deltaPercent").value(contains(nullValue())))
                // spent only in January: dropped to zero
                .andExpect(jsonPath("$.categories[?(@.category == 'Развлечения')].deltaMinor").value(contains(-64_000)))
                .andExpect(jsonPath("$.categories[?(@.category == 'Развлечения')].deltaPercent").value(contains(-100.0)))
                // unchanged
                .andExpect(jsonPath("$.categories[?(@.category == 'Жильё')].deltaMinor").value(contains(0)));
    }

    // ---- anomalies -------------------------------------------------------------------------

    @Test
    void oneHugePurchaseInAnOrdinaryCategoryIsFlagged() throws Exception {
        long user = newUser();
        long account = createAccount(user, "RUB");
        seedFoodWithOneOutlier(user, account);

        mvc.perform(get("/api/v1/reports/anomalies").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.threshold").value(2.0))
                // only the 9 000.00 purchase: the ordinary ones are compared with peers that include it
                .andExpect(jsonPath("$.anomalies.length()").value(1))
                .andExpect(jsonPath("$.anomalies[0].category").value("Food"))
                .andExpect(jsonPath("$.anomalies[0].amountMinor").value(900_000))
                .andExpect(jsonPath("$.anomalies[0].categoryAverageMinor").value(99_625))
                .andExpect(jsonPath("$.anomalies[0].zScore").value(greaterThan(100.0)));
    }

    @Test
    void categoriesWithTooFewExpensesAreNeverFlagged() throws Exception {
        long user = newUser();
        long account = createAccount(user, "RUB");
        long rare = createCategory(user, "Rare");
        // 4 expenses: the big one has only 3 peers, which cannot define what is normal
        addTransaction(user, account, rare, -100_000, "2025-01-01", "a");
        addTransaction(user, account, rare, -100_000, "2025-01-02", "b");
        addTransaction(user, account, rare, -100_000, "2025-01-03", "c");
        addTransaction(user, account, rare, -900_000, "2025-01-04", "huge");

        mvc.perform(get("/api/v1/reports/anomalies").with(as(user)))
                .andExpect(jsonPath("$.anomalies.length()").value(0));
    }

    @Test
    void dateRangeSelectsWhichAnomaliesAreReportedButNotTheBaseline() throws Exception {
        long user = newUser();
        long account = createAccount(user, "RUB");
        seedFoodWithOneOutlier(user, account);

        // the outlier is on 2025-01-09: a window that excludes it reports nothing...
        mvc.perform(get("/api/v1/reports/anomalies").with(as(user)).param("from", "2025-02-01"))
                .andExpect(jsonPath("$.anomalies.length()").value(0));
        // ...and one that includes it still scores it against the whole history
        mvc.perform(get("/api/v1/reports/anomalies").with(as(user)).param("from", "2025-01-09").param("to", "2025-01-09"))
                .andExpect(jsonPath("$.anomalies.length()").value(1));
    }

    @Test
    void aHigherThresholdReportsFewerAnomalies() throws Exception {
        long user = newUser();
        long account = createAccount(user, "RUB");
        seedFoodWithOneOutlier(user, account);

        mvc.perform(get("/api/v1/reports/anomalies").with(as(user)).param("threshold", "10"))
                .andExpect(jsonPath("$.anomalies.length()").value(1)); // z is in the hundreds, still above 10
        mvc.perform(get("/api/v1/reports/anomalies").with(as(user)).param("threshold", "0.5"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/anomalies").with(as(user)).param("threshold", "11"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theSampleStatementHasNoAnomalies() throws Exception {
        long user = newUser();
        importSample(user, createAccount(user, "RUB"));

        mvc.perform(get("/api/v1/reports/anomalies").with(as(user)))
                .andExpect(jsonPath("$.anomalies.length()").value(0));
    }

    // ---- forecast --------------------------------------------------------------------------

    @Test
    void forecastCombinesMovingAverageAndTrend() throws Exception {
        long user = newUser();
        importSample(user, createAccount(user, "RUB"));

        mvc.perform(get("/api/v1/reports/forecast").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastMonth").value("2025-03"))
                .andExpect(jsonPath("$.forecastMonth").value("2025-04"))
                .andExpect(jsonPath("$.history.length()").value(3))
                .andExpect(jsonPath("$.history[0].month").value("2025-01"))
                .andExpect(jsonPath("$.movingAverageMinor").value(4_293_402))
                .andExpect(jsonPath("$.trendMinor").value(4_701_327));
    }

    @Test
    void forecastWithTwoMonthsHasNoTrend() throws Exception {
        long user = newUser();
        long account = createAccount(user, "RUB");
        addTransaction(user, account, -1_000, "2025-01-10", "a");
        addTransaction(user, account, -3_000, "2025-02-10", "b");

        mvc.perform(get("/api/v1/reports/forecast").with(as(user)))
                .andExpect(jsonPath("$.movingAverageMinor").value(2_000))
                .andExpect(jsonPath("$.trendMinor").value(nullValue()));
    }

    @Test
    void forecastWithoutDataIsEmpty() throws Exception {
        long user = newUser();
        createAccount(user, "RUB");

        mvc.perform(get("/api/v1/reports/forecast").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastMonth").value(nullValue()))
                .andExpect(jsonPath("$.history.length()").value(0))
                .andExpect(jsonPath("$.movingAverageMinor").value(nullValue()));
    }

    // ---- currencies, ownership, validation -------------------------------------------------

    @Test
    void severalCurrenciesAreNeverMixed() throws Exception {
        long user = newUser();
        long rub = createAccount(user, "RUB");
        long usd = createAccount(user, "USD");
        importSample(user, rub);
        addTransaction(user, usd, -500, "2025-01-05", "Coffee");

        mvc.perform(get("/api/v1/reports/spending-by-category").with(as(user)))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/reports/spending-by-category").with(as(user)).param("currency", "USD"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.totalMinor").value(500));

        mvc.perform(get("/api/v1/reports/spending-by-category").with(as(user)).param("accountId", String.valueOf(rub)))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.totalMinor").value(12_880_205));

        mvc.perform(get("/api/v1/reports/spending-by-category").with(as(user))
                        .param("accountId", String.valueOf(rub)).param("currency", "USD"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsOnlyCoverTheCallersOwnData() throws Exception {
        long alice = newUser();
        importSample(alice, createAccount(alice, "RUB"));
        long bob = newUser();
        createAccount(bob, "RUB");

        mvc.perform(get("/api/v1/reports/spending-by-category").with(as(bob)))
                .andExpect(jsonPath("$.totalMinor").value(0))
                .andExpect(jsonPath("$.categories.length()").value(0));
        mvc.perform(get("/api/v1/reports/monthly").with(as(bob)))
                .andExpect(jsonPath("$.months.length()").value(0));
    }

    @Test
    void anotherUsersAccountIsNotFound() throws Exception {
        long alice = newUser();
        long aliceAccount = createAccount(alice, "RUB");
        long bob = newUser();

        mvc.perform(get("/api/v1/reports/monthly").with(as(bob)).param("accountId", String.valueOf(aliceAccount)))
                .andExpect(status().isNotFound());
    }

    @Test
    void userWithoutAccountsGetsEmptyReports() throws Exception {
        long user = newUser();

        mvc.perform(get("/api/v1/reports/spending-by-category").with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value(nullValue()))
                .andExpect(jsonPath("$.categories.length()").value(0));
    }

    @Test
    void invalidParametersAreRejected() throws Exception {
        long user = newUser();
        createAccount(user, "RUB");

        mvc.perform(get("/api/v1/reports/monthly").with(as(user))
                        .param("from", "2025-03-01").param("to", "2025-01-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/top-merchants").with(as(user)).param("limit", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/top-merchants").with(as(user)).param("limit", "51"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/month-comparison").with(as(user)).param("month", "2025-13"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/month-comparison").with(as(user)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/monthly").with(as(user)).param("currency", "rub"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/reports/monthly")).andExpect(status().isUnauthorized());
    }

    // ---- helpers --------------------------------------------------------------------------

    private long newUser() {
        String email = "reports-" + UUID.randomUUID() + "@local";
        jdbc.update("INSERT INTO users (email, password_hash) VALUES (?, 'x')", email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private static RequestPostProcessor as(long userId) {
        return jwt().jwt(token -> token.subject(String.valueOf(userId)));
    }

    private long createAccount(long user, String currency) throws Exception {
        String json = mvc.perform(post("/api/v1/accounts").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Account %s", "currency": "%s"}""".formatted(UUID.randomUUID(), currency)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private void importSample(long user, long account) throws Exception {
        mvc.perform(multipart("/api/v1/accounts/{id}/import", account)
                        .file(new MockMultipartFile("file", "sample.csv", "text/csv", Files.readAllBytes(SAMPLE)))
                        .with(as(user)))
                .andExpect(status().isOk());
    }

    private void addTransaction(long user, long account, long amountMinor, String date, String description)
            throws Exception {
        mvc.perform(post("/api/v1/transactions").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": %d, "amountMinor": %d, "occurredOn": "%s", "description": "%s"}"""
                                .formatted(account, amountMinor, date, description)))
                .andExpect(status().isCreated());
    }

    private void addTransaction(long user, long account, long categoryId, long amountMinor, String date,
            String description) throws Exception {
        mvc.perform(post("/api/v1/transactions").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": %d, "categoryId": %d, "amountMinor": %d, "occurredOn": "%s",
                                 "description": "%s"}""".formatted(account, categoryId, amountMinor, date, description)))
                .andExpect(status().isCreated());
    }

    private long createCategory(long user, String name) throws Exception {
        String json = mvc.perform(post("/api/v1/categories").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s"}""".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    /** Eight ordinary "Food" expenses of about 1 000.00 (mean 996.25) and one of 9 000.00 on 9 Jan. */
    private void seedFoodWithOneOutlier(long user, long account) throws Exception {
        long food = createCategory(user, "Food");
        long[] ordinary = {-100_000, -95_000, -105_000, -98_000, -102_000, -99_000, -101_000, -97_000};
        for (int i = 0; i < ordinary.length; i++) {
            addTransaction(user, account, food, ordinary[i], "2025-01-0" + (i + 1), "groceries");
        }
        addTransaction(user, account, food, -900_000, "2025-01-09", "party");
    }
}
