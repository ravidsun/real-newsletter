package com.realnewsletter.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for JWT token generation and validation.
 *
 * <p>Bound from the {@code jwt.*} namespace in application properties.</p>
 */
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * HMAC-SHA256 signing secret (Base64-encoded, at least 256 bits recommended).
     * Set via {@code JWT_SECRET} environment variable or {@code jwt.secret} property.
     */
    private String secret = "changeme-this-must-be-at-least-256-bits-long-for-hs256";

    /**
     * Access token time-to-live in minutes. Defaults to 15 minutes.
     */
    private long accessTokenTtlMinutes = 15;

    /**
     * Refresh token time-to-live in days. Defaults to 7 days.
     */
    private long refreshTokenTtlDays = 7;

    /**
     * Cookie name used for the HttpOnly refresh token.
     */
    private String refreshCookieName = "refresh_token";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessTokenTtlMinutes() {
        return accessTokenTtlMinutes;
    }

    public void setAccessTokenTtlMinutes(long accessTokenTtlMinutes) {
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
    }

    public long getRefreshTokenTtlDays() {
        return refreshTokenTtlDays;
    }

    public void setRefreshTokenTtlDays(long refreshTokenTtlDays) {
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    public String getRefreshCookieName() {
        return refreshCookieName;
    }

    public void setRefreshCookieName(String refreshCookieName) {
        this.refreshCookieName = refreshCookieName;
    }
}

