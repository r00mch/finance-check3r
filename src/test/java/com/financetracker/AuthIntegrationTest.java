package com.financetracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Exercises the real security chain: no mocked authentication, tokens come from /auth. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    private static final Path TEST_DB = Path.of("target", "test-db", "auth.db");

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

    @Test
    void registerReturnsATokenThatOpensProtectedEndpoints() throws Exception {
        String token = register(uniqueEmail(), "correct-horse");

        mvc.perform(get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void registerStoresOnlyAHashOfThePassword() throws Exception {
        String email = uniqueEmail();
        register(email, "correct-horse");

        String stored = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, email);

        assertThat(stored).startsWith("$2").doesNotContain("correct-horse");
    }

    @Test
    void duplicateEmailIsRejectedEvenWithDifferentCase() throws Exception {
        String email = uniqueEmail();
        register(email, "correct-horse");

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email.toUpperCase(), "another-pass")))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("not-an-email", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("email")))
                .andExpect(jsonPath("$.errors", hasKey("password")));
    }

    @Test
    void loginWithCorrectPasswordReturnsAWorkingToken() throws Exception {
        String email = uniqueEmail();
        register(email, "correct-horse");

        String json = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, "correct-horse")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + JsonPath.read(json, "$.accessToken")))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
        String email = uniqueEmail();
        register(email, "correct-horse");

        String wrongPassword = login(email, "wrong-password");
        String unknownEmail = login(uniqueEmail(), "whatever-pass");

        assertThat(wrongPassword).isEqualTo(unknownEmail);
    }

    @Test
    void protectedEndpointsRejectMissingOrBrokenTokens() throws Exception {
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        // a structurally valid HS256 JWT, but signed with a different secret
        String forged = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiZXhwIjo0MTAyNDQ0ODAwfQ"
                + ".AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

        mvc.perform(get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersCannotSeeEachOthersData() throws Exception {
        String alice = register(uniqueEmail(), "alice-password");
        String bob = register(uniqueEmail(), "bob-password");

        String created = mvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Alice only", "currency": "RUB"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number aliceAccountId = JsonPath.read(created, "$.id");

        mvc.perform(get("/api/v1/accounts/" + aliceAccountId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bob))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer " + bob))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void healthAndApiDocsArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    // ---- helpers --------------------------------------------------------------------------

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private static String credentials(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}""".formatted(email, password);
    }

    private String register(String email, String password) throws Exception {
        String json = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    /** Failed login: returns the response body so two failures can be compared. */
    private String login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, password)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
    }
}
