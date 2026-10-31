package com.financetracker.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, @DefaultValue("1h") Duration expiration) {

    private static final int MIN_SECRET_BYTES = 32; // HS256 needs a 256-bit key

    public JwtProperties {
        // fail at startup rather than run with a guessable signing key
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes (set JWT_SECRET)");
        }
    }
}
