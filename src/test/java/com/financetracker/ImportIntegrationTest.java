package com.financetracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AsTestUser.class)
class ImportIntegrationTest {

    private static final Path TEST_DB = Path.of("target", "test-db", "import.db");
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

    @BeforeEach
    void ensureTestUserExists() {
        jdbc.update("INSERT OR IGNORE INTO users (id, email, password_hash) VALUES (?, 'import-test@local', 'x')",
                AsTestUser.ID);
    }

    @Test
    void importsTheSampleStatement() throws Exception {
        long account = createAccount();

        upload(account, Files.readAllBytes(SAMPLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsRead").value(30))
                .andExpect(jsonPath("$.imported").value(30))
                .andExpect(jsonPath("$.duplicates").value(0))
                .andExpect(jsonPath("$.failed").value(0));

        // income 255 000.00 minus expenses 128 802.05
        mvc.perform(get("/api/v1/accounts/" + account))
                .andExpect(jsonPath("$.balanceMinor").value(12_619_795));
        mvc.perform(get("/api/v1/categories"))
                .andExpect(jsonPath("$[?(@.name == 'Продукты')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Зарплата')]").exists());
    }

    @Test
    void uploadingTheSameFileAgainCreatesNoDuplicates() throws Exception {
        long account = createAccount();
        byte[] csv = Files.readAllBytes(SAMPLE);
        upload(account, csv).andExpect(jsonPath("$.imported").value(30));

        upload(account, csv)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.duplicates").value(30));

        mvc.perform(get("/api/v1/transactions").param("accountId", String.valueOf(account)))
                .andExpect(jsonPath("$.page.totalElements").value(30));
    }

    @Test
    void identicalRowsInOneFileAreBothKept() throws Exception {
        long account = createAccount();
        String csv = """
                date,amount,description
                2025-01-03,-180,Coffee
                2025-01-03,-180,Coffee
                """;

        upload(account, csv.getBytes(StandardCharsets.UTF_8))
                .andExpect(jsonPath("$.imported").value(2));
        upload(account, csv.getBytes(StandardCharsets.UTF_8))
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.duplicates").value(2));
    }

    @Test
    void anOverlappingStatementOnlyAddsTheNewRows() throws Exception {
        long account = createAccount();
        upload(account, "date,amount,description\n2025-01-01,-10,A\n2025-01-02,-20,B\n"
                .getBytes(StandardCharsets.UTF_8));

        upload(account, "date,amount,description\n2025-01-02,-20,B\n2025-01-03,-30,C\n"
                .getBytes(StandardCharsets.UTF_8))
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.duplicates").value(1));
    }

    @Test
    void badRowsAreReportedWhileGoodRowsAreImported() throws Exception {
        long account = createAccount();
        String csv = """
                date,amount,description
                2025-01-01,-10,fine
                not-a-date,-10,broken
                2025-01-03,-30,fine too
                """;

        upload(account, csv.getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.errors[0].row").value(2));
    }

    @Test
    void fileWithoutRequiredColumnsIsRejected() throws Exception {
        long account = createAccount();

        upload(account, "foo,bar\n1,2\n".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        long account = createAccount();

        upload(account, new byte[0]).andExpect(status().isBadRequest());
    }

    @Test
    void importIntoAnotherUsersAccountLooksLikeAMissingAccount() throws Exception {
        String email = "other-" + UUID.randomUUID().toString().substring(0, 8) + "@local";
        jdbc.update("INSERT INTO users (email, password_hash) VALUES (?, 'x')", email);
        Long otherUser = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        jdbc.update("INSERT INTO accounts (user_id, name) VALUES (?, 'Not yours')", otherUser);
        Long foreignAccount = jdbc.queryForObject("SELECT id FROM accounts WHERE user_id = ?", Long.class, otherUser);

        upload(foreignAccount, Files.readAllBytes(SAMPLE)).andExpect(status().isNotFound());
    }

    // ---- helpers --------------------------------------------------------------------------

    private ResultActions upload(long accountId, byte[] content) throws Exception {
        return mvc.perform(multipart("/api/v1/accounts/{id}/import", accountId)
                .file(new MockMultipartFile("file", "statement.csv", "text/csv", content)));
    }

    private long createAccount() throws Exception {
        String json = mvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Import %s", "currency": "RUB"}""".formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }
}
