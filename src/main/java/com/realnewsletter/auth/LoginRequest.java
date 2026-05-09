package com.realnewsletter.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for authentication login requests.
 *
 * @param username the principal's username
 * @param password the principal's password (plaintext; compared against encoded store)
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {
}

