package com.realnewsletter.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Service responsible for generating and validating HMAC-SHA256 signed JWT access tokens.
 *
 * <p>Access tokens are short-lived (configurable via {@code jwt.access-token-ttl-minutes},
 * default 15 minutes). They carry the subject (username) and a list of roles as claims.</p>
 *
 * <p>This service is intentionally stateless — it does not track issued tokens.
 * Revocation is handled at the refresh-token layer via {@link RefreshTokenStore}.</p>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String ROLES_CLAIM = "roles";

    private final JwtProperties props;
    private final SecretKey signingKey;

    public JwtService(JwtProperties props) {
        this.props = props;
        // Derive a HMAC-SHA256 key from the configured secret bytes
        this.signingKey = Keys.hmacShaKeyFor(
                props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a short-lived JWT access token for the given subject and roles.
     *
     * @param subject the username / principal name to embed as the JWT subject
     * @param roles   comma-separated list of Spring Security role names (e.g. "ROLE_ADMIN")
     * @return compact serialised JWT string
     */
    public String generateAccessToken(String subject, String roles) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(props.getAccessTokenTtlMinutes() * 60L);

        return Jwts.builder()
                .subject(subject)
                .claim(ROLES_CLAIM, roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and validates the JWT token, returning its claims.
     *
     * @param token the compact JWT string
     * @return parsed {@link Claims} if the token is valid and not expired
     * @throws JwtException if the token is invalid, expired, or tampered with
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the subject (username) from a valid JWT.
     *
     * @param token the compact JWT string
     * @return the subject claim, or {@code null} if the token is invalid
     */
    public String extractSubject(String token) {
        try {
            return parseToken(token).getSubject();
        } catch (JwtException ex) {
            log.debug("JWT subject extraction failed: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Returns {@code true} if the token can be parsed and is not expired.
     *
     * @param token the compact JWT string
     * @return {@code true} if valid; {@code false} otherwise
     */
    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Extracts the roles claim from a valid JWT.
     *
     * @param token the compact JWT string
     * @return roles string or empty string if absent/invalid
     */
    public String extractRoles(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.get(ROLES_CLAIM, String.class);
        } catch (JwtException ex) {
            log.debug("JWT roles extraction failed: {}", ex.getMessage());
            return "";
        }
    }
}

