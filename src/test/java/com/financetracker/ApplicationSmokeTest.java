package com.financetracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ApplicationSmokeTest {

    // Lives in target/ rather than @TempDir: on Windows the Spring context still holds
    // the file open when JUnit tries to delete a temp directory.
    private static final Path TEST_DB = Path.of("target", "test-db", "smoke.db");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws IOException {
        Files.createDirectories(TEST_DB.getParent());
        Files.deleteIfExists(TEST_DB);
        registry.add("spring.datasource.url",
                () -> "jdbc:sqlite:" + TEST_DB + "?foreign_keys=on");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestRestTemplate rest;

    @Test
    void flywayCreatesCoreTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'table'", String.class);

        assertThat(tables).contains("users", "accounts", "categories", "transactions");
    }

    @Test
    void foreignKeysAreEnforced() {
        Integer enabled = jdbc.queryForObject("PRAGMA foreign_keys", Integer.class);

        assertThat(enabled).isEqualTo(1);
    }

    @Test
    void healthEndpointIsUp() {
        var response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }
}
