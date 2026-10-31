package com.financetracker;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Makes every request of the autowired MockMvc arrive authenticated as user {@link #ID}, so API
 * tests do not have to log in. The tests insert that user row themselves.
 * (AuthIntegrationTest deliberately does not use this: it tests the real login flow.)
 */
@TestConfiguration
class AsTestUser {

    static final long ID = 1L;

    @Bean
    MockMvcBuilderCustomizer authenticateAsTestUser() {
        return builder -> builder.defaultRequest(get("/").with(jwt().jwt(token -> token.subject(String.valueOf(ID)))));
    }
}
