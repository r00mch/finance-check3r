package com.financetracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AsTestUser.class)
class ApiIntegrationTest {

    private static final Path TEST_DB = Path.of("target", "test-db", "api.db");

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

    @BeforeEach
    void ensureTestUserExists() {
        jdbc.update("INSERT OR IGNORE INTO users (id, email, password_hash) VALUES (?, 'api-test@local', 'x')",
                AsTestUser.ID);
    }

    // ---- accounts -------------------------------------------------------------------------

    @Test
    void createAccount_returns201WithLocationAndZeroBalance() throws Exception {
        mvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Card %s", "currency": "RUB"}""".formatted(unique())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.balanceMinor").value(0));
    }

    @Test
    void duplicateAccountName_returns409() throws Exception {
        String name = "Dup " + unique();
        createAccount(name);

        mvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "currency": "RUB"}""".formatted(name)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidAccount_returns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": " ", "currency": "rub"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("name")))
                .andExpect(jsonPath("$.errors", hasKey("currency")));
    }

    @Test
    void unknownAccount_returns404() throws Exception {
        mvc.perform(get("/api/v1/accounts/999999")).andExpect(status().isNotFound());
    }

    @Test
    void balanceIsSumOfTransactions() throws Exception {
        long account = createAccount("Balance " + unique());
        createTransaction(account, null, 50_000, "2025-01-10");
        createTransaction(account, null, -15_050, "2025-01-11");

        mvc.perform(get("/api/v1/accounts/" + account))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceMinor").value(34_950));
    }

    @Test
    void deleteAccount_removesItsTransactions() throws Exception {
        long account = createAccount("Doomed " + unique());
        long tx = createTransaction(account, null, -100, "2025-01-10");

        mvc.perform(delete("/api/v1/accounts/" + account)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/transactions/" + tx)).andExpect(status().isNotFound());
    }

    // ---- categories -----------------------------------------------------------------------

    @Test
    void duplicateCategoryName_returns409() throws Exception {
        String name = "Food " + unique();
        createCategory(name);

        mvc.perform(post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s"}""".formatted(name)))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteCategory_keepsTransactionsButClearsCategory() throws Exception {
        long account = createAccount("Acc " + unique());
        long category = createCategory("Temp " + unique());
        long tx = createTransaction(account, category, -500, "2025-02-01");

        mvc.perform(delete("/api/v1/categories/" + category)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/transactions/" + tx))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").doesNotExist());
    }

    // ---- transactions ---------------------------------------------------------------------

    @Test
    void zeroAmount_returns400() throws Exception {
        long account = createAccount("Zero " + unique());

        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": %d, "amountMinor": 0, "occurredOn": "2025-01-01"}"""
                                .formatted(account)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filtersByCategoryAndDateRange() throws Exception {
        long account = createAccount("Filter " + unique());
        long food = createCategory("Food " + unique());
        long taxi = createCategory("Taxi " + unique());
        createTransaction(account, food, -1_000, "2025-03-01");
        createTransaction(account, food, -2_000, "2025-03-15");
        createTransaction(account, taxi, -3_000, "2025-03-20");

        mvc.perform(get("/api/v1/transactions").param("accountId", String.valueOf(account))
                        .param("categoryId", String.valueOf(food)))
                .andExpect(jsonPath("$.page.totalElements").value(2));

        mvc.perform(get("/api/v1/transactions").param("accountId", String.valueOf(account))
                        .param("from", "2025-03-10").param("to", "2025-03-20"))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void listIsPagedNewestFirst() throws Exception {
        long account = createAccount("Paged " + unique());
        createTransaction(account, null, -100, "2025-04-01");
        createTransaction(account, null, -200, "2025-04-03");
        createTransaction(account, null, -300, "2025-04-02");

        mvc.perform(get("/api/v1/transactions").param("accountId", String.valueOf(account))
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].occurredOn").value("2025-04-03"))
                .andExpect(jsonPath("$.content[1].occurredOn").value("2025-04-02"))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void unknownSortProperty_returns400() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("sort", "nonsense,asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTransaction_changesFields() throws Exception {
        long account = createAccount("Upd " + unique());
        long tx = createTransaction(account, null, -100, "2025-05-01");

        mvc.perform(put("/api/v1/transactions/" + tx).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": %d, "amountMinor": -999, "occurredOn": "2025-05-02",
                                 "description": "fixed"}""".formatted(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountMinor").value(-999))
                .andExpect(jsonPath("$.occurredOn").value("2025-05-02"))
                .andExpect(jsonPath("$.description").value("fixed"));
    }

    @Test
    void anotherUsersAccountLooksNonExistent() throws Exception {
        String email = "other-" + unique() + "@local";
        jdbc.update("INSERT INTO users (email, password_hash) VALUES (?, 'x')", email);
        Long otherUser = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        jdbc.update("INSERT INTO accounts (user_id, name) VALUES (?, 'Secret')", otherUser);
        Long foreignAccount = jdbc.queryForObject(
                "SELECT id FROM accounts WHERE user_id = ?", Long.class, otherUser);
        assertThat(foreignAccount).isNotNull();

        mvc.perform(get("/api/v1/accounts/" + foreignAccount)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": %d, "amountMinor": -1, "occurredOn": "2025-01-01"}"""
                                .formatted(foreignAccount)))
                .andExpect(status().isNotFound());
    }

    @Test
    void openApiDocumentDescribesTheEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/transactions")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/accounts/{id}")));
    }

    // ---- helpers --------------------------------------------------------------------------

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private long createAccount(String name) throws Exception {
        return idOf(mvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "currency": "RUB"}""".formatted(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private long createCategory(String name) throws Exception {
        return idOf(mvc.perform(post("/api/v1/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s"}""".formatted(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private long createTransaction(long accountId, Long categoryId, long amountMinor, String date)
            throws Exception {
        String category = categoryId == null ? "null" : categoryId.toString();
        return idOf(mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": %d, "categoryId": %s, "amountMinor": %d, "occurredOn": "%s"}"""
                                .formatted(accountId, category, amountMinor, date)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private static long idOf(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
