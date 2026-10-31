package com.financetracker.common;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Temporary stand-in for authentication (removed in stage 3). */
@Component
public class DemoUser implements CurrentUser, ApplicationRunner {

    static final long ID = 1L;

    private final JdbcTemplate jdbc;

    DemoUser(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Long id() {
        return ID;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.update("INSERT OR IGNORE INTO users (id, email, password_hash) VALUES (?, 'demo@local', 'n/a')", ID);
    }
}
