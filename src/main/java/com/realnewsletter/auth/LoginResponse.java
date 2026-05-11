package com.realnewsletter.auth;

/**
 * DTO for the access token returned in the login and refresh responses.
 *
 * <p>The refresh token is <em>not</em> included here — it is delivered exclusively
 * as an HttpOnly, Secure cookie to prevent JavaScript access.</p>
 *
 * @param accessToken the short-lived JWT access token
 * @param tokenType   always {@code "Bearer"}
 * @param expiresIn   access token lifetime in seconds
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn) {
}

