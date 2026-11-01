package com.financetracker;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Budgets are checked against samples/statement-sample.csv. February 2025 spending in that file:
 * Продукты 7 275.50, Кафе 4 200.00, Жильё 25 000.00, Транспорт 320.00. "Today" is fixed at 14 Feb 2025.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BudgetIntegrationTest {

    private static final Path TEST_DB = Path.of("target", "test-db", "budgets.db");
    private static final Path SAMPLE = Path.of("samples", "statement-sample.csv");

    @TestConfiguration
    static class FixedClock {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2025-02-14T10:00:00Z"), ZoneOffset.UTC);
        }
    }

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

    // ---- CRUD ------------------------------------------------------------------------------

    @Test
    void createReadUpdateDeleteABudget() throws Exception {
        long user = newUser();
        long food = createCategory(user, "Food");

        String created = mvc.perform(post("/api/v1/budgets").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(budgetJson(food, "RUB", 500_000)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.category").value("Food"))
                .andExpect(jsonPath("$.limitMinor").value(500_000))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        mvc.perform(put("/api/v1/budgets/" + id).with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(budgetJson(food, "RUB", 750_000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limitMinor").value(750_000));
        mvc.perform(get("/api/v1/budgets").with(as(user)))
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(delete("/api/v1/budgets/" + id).with(as(user))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/budgets/" + id).with(as(user))).andExpect(status().isNotFound());
    }

    @Test
    void secondBudgetForTheSameCategoryAndCurrencyIsRejected() throws Exception {
        long user = newUser();
        long food = createCategory(user, "Food");
        createBudget(user, food, "RUB", 500_000);

        mvc.perform(post("/api/v1/budgets").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(budgetJson(food, "RUB", 1)))
                .andExpect(status().isConflict());
        // the same category in another currency is a different budget
        mvc.perform(post("/api/v1/budgets").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(budgetJson(food, "USD", 1_000)))
                .andExpect(status().isCreated());
    }

    @Test
    void invalidBudgetsAreRejected() throws Exception {
        long user = newUser();
        long food = createCategory(user, "Food");

        mvc.perform(post("/api/v1/budgets").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(budgetJson(food, "RUB", 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/budgets").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(budgetJson(food, "rub", 100)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/budgets").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(budgetJson(999_999, "RUB", 100)))
                .andExpect(status().isNotFound());
    }

    @Test
    void budgetsAreIsolatedBetweenUsers() throws Exception {
        long alice = newUser();
        long aliceFood = createCategory(alice, "Food");
        long aliceBudget = createBudget(alice, aliceFood, "RUB", 500_000);
        long bob = newUser();

        mvc.perform(get("/api/v1/budgets").with(as(bob))).andExpect(jsonPath("$").value(empty()));
        mvc.perform(get("/api/v1/budgets/" + aliceBudget).with(as(bob))).andExpect(status().isNotFound());
        // Alice's category cannot be used by Bob either
        mvc.perform(post("/api/v1/budgets").with(as(bob)).contentType(MediaType.APPLICATION_JSON)
                        .content(budgetJson(aliceFood, "RUB", 100)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingACategoryRemovesItsBudgets() throws Exception {
        long user = newUser();
        long food = createCategory(user, "Food");
        createBudget(user, food, "RUB", 500_000);

        mvc.perform(delete("/api/v1/categories/" + food).with(as(user))).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/budgets").with(as(user))).andExpect(jsonPath("$").value(empty()));
    }

    // ---- status ----------------------------------------------------------------------------

    @Test
    void statusShowsSpentRemainingStateAndProjection() throws Exception {
        long user = newUser();
        importSample(user, createAccount(user, "RUB"));
        createBudget(user, categoryId(user, "Продукты"), "RUB", 800_000);
        createBudget(user, categoryId(user, "Кафе"), "RUB", 400_000);
        createBudget(user, categoryId(user, "Жильё"), "RUB", 3_000_000);
        createBudget(user, categoryId(user, "Транспорт"), "RUB", 100_000);

        mvc.perform(get("/api/v1/budgets/status").with(as(user)).param("month", "2025-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                // 7 275.50 of 8 000: 90.9 %, close to the limit; at this pace it will be exceeded
                .andExpect(jsonPath("$[?(@.category == 'Продукты')].spentMinor").value(contains(727_550)))
                .andExpect(jsonPath("$[?(@.category == 'Продукты')].remainingMinor").value(contains(72_450)))
                .andExpect(jsonPath("$[?(@.category == 'Продукты')].usedPercent").value(contains(90.9)))
                .andExpect(jsonPath("$[?(@.category == 'Продукты')].state").value(contains("WARNING")))
                .andExpect(jsonPath("$[?(@.category == 'Продукты')].projectedMinor").value(contains(1_455_100)))
                .andExpect(jsonPath("$[?(@.category == 'Продукты')].projectedToExceed").value(contains(true)))
                // 4 200 of 4 000: over the limit
                .andExpect(jsonPath("$[?(@.category == 'Кафе')].state").value(contains("EXCEEDED")))
                .andExpect(jsonPath("$[?(@.category == 'Кафе')].remainingMinor").value(contains(-20_000)))
                .andExpect(jsonPath("$[?(@.category == 'Кафе')].usedPercent").value(contains(105.0)))
                // 25 000 of 30 000 rent: 83.3 %
                .andExpect(jsonPath("$[?(@.category == 'Жильё')].usedPercent").value(contains(83.3)))
                .andExpect(jsonPath("$[?(@.category == 'Жильё')].state").value(contains("WARNING")))
                // 320 of 1 000: comfortable, and the projection (640) stays under the limit
                .andExpect(jsonPath("$[?(@.category == 'Транспорт')].state").value(contains("OK")))
                .andExpect(jsonPath("$[?(@.category == 'Транспорт')].projectedToExceed").value(contains(false)));
    }

    @Test
    void statusDefaultsToTheCurrentMonthOfTheClock() throws Exception {
        long user = newUser();
        importSample(user, createAccount(user, "RUB"));
        createBudget(user, categoryId(user, "Продукты"), "RUB", 800_000);

        mvc.perform(get("/api/v1/budgets/status").with(as(user)))
                .andExpect(jsonPath("$[0].month").value("2025-02"))
                .andExpect(jsonPath("$[0].spentMinor").value(727_550));
    }

    @Test
    void finishedMonthIsNotExtrapolated() throws Exception {
        long user = newUser();
        importSample(user, createAccount(user, "RUB"));
        createBudget(user, categoryId(user, "Продукты"), "RUB", 800_000);

        // January is over by the fixed "today": projection equals what was actually spent
        mvc.perform(get("/api/v1/budgets/status").with(as(user)).param("month", "2025-01"))
                .andExpect(jsonPath("$[0].spentMinor").value(671_165))
                .andExpect(jsonPath("$[0].projectedMinor").value(671_165))
                .andExpect(jsonPath("$[0].projectedToExceed").value(false));
    }

    @Test
    void aBudgetIgnoresSpendingInOtherCurrencies() throws Exception {
        long user = newUser();
        long rub = createAccount(user, "RUB");
        long usd = createAccount(user, "USD");
        importSample(user, rub);
        long groceries = categoryId(user, "Продукты");
        createBudget(user, groceries, "RUB", 800_000);
        // 99.99 USD spent on groceries in February must not count against the RUB budget
        mvc.perform(post("/api/v1/transactions").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": %d, "categoryId": %d, "amountMinor": -9999, "occurredOn": "2025-02-10"}"""
                                .formatted(usd, groceries)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/budgets/status").with(as(user)).param("month", "2025-02"))
                .andExpect(jsonPath("$[0].spentMinor").value(727_550));
    }

    @Test
    void budgetWithoutSpendingShowsZeroUsed() throws Exception {
        long user = newUser();
        createAccount(user, "RUB");
        createBudget(user, createCategory(user, "Food"), "RUB", 500_000);

        mvc.perform(get("/api/v1/budgets/status").with(as(user)).param("month", "2025-02"))
                .andExpect(jsonPath("$[0].spentMinor").value(0))
                .andExpect(jsonPath("$[0].usedPercent").value(0.0))
                .andExpect(jsonPath("$[0].state").value("OK"));
    }

    @Test
    void invalidMonthIsRejectedAndAuthenticationIsRequired() throws Exception {
        long user = newUser();

        mvc.perform(get("/api/v1/budgets/status").with(as(user)).param("month", "2025-13"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/budgets")).andExpect(status().isUnauthorized());
    }

    // ---- helpers --------------------------------------------------------------------------

    private long newUser() {
        String email = "budgets-" + UUID.randomUUID() + "@local";
        jdbc.update("INSERT INTO users (email, password_hash) VALUES (?, 'x')", email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private static RequestPostProcessor as(long userId) {
        return jwt().jwt(token -> token.subject(String.valueOf(userId)));
    }

    private static String budgetJson(long categoryId, String currency, long limitMinor) {
        return """
                {"categoryId": %d, "currency": "%s", "limitMinor": %d}""".formatted(categoryId, currency, limitMinor);
    }

    private long createAccount(long user, String currency) throws Exception {
        String json = mvc.perform(post("/api/v1/accounts").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Account %s", "currency": "%s"}""".formatted(UUID.randomUUID(), currency)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private long createCategory(long user, String name) throws Exception {
        String json = mvc.perform(post("/api/v1/categories").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s"}""".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private long createBudget(long user, long categoryId, String currency, long limitMinor) throws Exception {
        String json = mvc.perform(post("/api/v1/budgets").with(as(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(budgetJson(categoryId, currency, limitMinor)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    /** Looks a category up by name, e.g. one created by the CSV import. */
    private long categoryId(long user, String name) throws Exception {
        String json = mvc.perform(get("/api/v1/categories").with(as(user)))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(json, "$[?(@.name == '" + name + "')].id");
        return ids.getFirst();
    }

    private void importSample(long user, long account) throws Exception {
        mvc.perform(multipart(URI.create("/api/v1/accounts/" + account + "/import"))
                        .file(new MockMultipartFile("file", "sample.csv", "text/csv", Files.readAllBytes(SAMPLE)))
                        .with(as(user)))
                .andExpect(status().isOk());
    }
}
