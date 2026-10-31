package com.financetracker.auth;

/** Send the token as {@code Authorization: Bearer <accessToken>}. */
public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
