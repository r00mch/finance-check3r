package com.financetracker.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AccountRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO code, e.g. RUB") String currency) {
}
