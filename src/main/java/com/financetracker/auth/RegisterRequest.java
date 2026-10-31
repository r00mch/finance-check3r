package com.financetracker.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // BCrypt ignores everything after 72 bytes, so longer passwords would be silently truncated
        @NotBlank @Size(min = 8, max = 72) String password) {
}
