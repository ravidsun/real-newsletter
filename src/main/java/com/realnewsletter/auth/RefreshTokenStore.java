package com.realnewsletter.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for opaque refresh tokens.
 *
 * <p>Each refresh token is a cryptographically random UUID mapped to the
 * owning username and an expiry timestamp. Tokens are validated on use and
 * expired entries are lazily evicted.</p>
 *
 * <p><b>Note:</b> This in-memory implementation is suitable for single-node
 * deployments. For multi-node or persistent storage, replace with a
 * database-backed implementation.</p>
 */
@Service
public class RefreshTokenStore {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenStore.class);

    /** Value object holding the owner and expiry of a refresh token. */
    record Entry(String username, Instant expiresAt) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final JwtProperties props;

    public RefreshTokenStore(JwtProperties props) {
        this.props = props;
    }

    /**
     * Creates and stores a new refresh token for the given username.
     *
     * @param username the principal this refresh token belongs to
     * @return the opaque refresh token string (a random UUID)
     */
    public String createToken(String username) {
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(props.getRefreshTokenTtlDays() * 86400L);
        store.put(token, new Entry(username, expiresAt));
        log.debug("Refresh token created for user '{}', expires at {}", username, expiresAt);
        return token;
    }

    /**
     * Looks up a refresh token and returns the owning username if the token is
     * present and not expired.
     *
     * @param token the opaque refresh token to validate
     * @return the username, or {@code null} if invalid / expired
     */
    public String validate(String token) {
        Entry entry = store.get(token);
        if (entry == null) {
            log.debug("Refresh token not found in store");
            return null;
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            store.remove(token);
            log.debug("Refresh token expired — evicted");
            return null;
        }
        return entry.username();
    }

    /**
     * Invalidates (removes) a specific refresh token.
     *
     * @param token the token to revoke
     */
    public void revoke(String token) {
        store.remove(token);
        log.debug("Refresh token revoked");
    }

    /**
     * Invalidates all refresh tokens belonging to the given username.
     *
     * @param username the user whose tokens should all be revoked
     */
    public void revokeAll(String username) {
        store.entrySet().removeIf(e -> e.getValue().username().equals(username));
        log.debug("All refresh tokens revoked for user '{}'", username);
    }
}

