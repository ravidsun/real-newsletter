package com.realnewsletter.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RefreshTokenStore}.
 */
class RefreshTokenStoreTest {

    private RefreshTokenStore store;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setRefreshTokenTtlDays(7);
        store = new RefreshTokenStore(props);
    }

    @Test
    @DisplayName("createToken returns a non-null token")
    void createToken_returnsNonNull() {
        String token = store.createToken("alice");
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("createToken generates unique tokens per call")
    void createToken_isUnique() {
        String t1 = store.createToken("alice");
        String t2 = store.createToken("alice");
        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    @DisplayName("validate returns username for a valid token")
    void validate_validToken_returnsUsername() {
        String token = store.createToken("bob");
        assertThat(store.validate(token)).isEqualTo("bob");
    }

    @Test
    @DisplayName("validate returns null for an unknown token")
    void validate_unknownToken_returnsNull() {
        assertThat(store.validate("non-existent-token")).isNull();
    }

    @Test
    @DisplayName("revoke makes the token invalid")
    void revoke_invalidatesToken() {
        String token = store.createToken("charlie");
        store.revoke(token);
        assertThat(store.validate(token)).isNull();
    }

    @Test
    @DisplayName("revokeAll removes all tokens for the given user")
    void revokeAll_removesAllUserTokens() {
        String t1 = store.createToken("dave");
        String t2 = store.createToken("dave");
        String t3 = store.createToken("eve");

        store.revokeAll("dave");

        assertThat(store.validate(t1)).isNull();
        assertThat(store.validate(t2)).isNull();
        assertThat(store.validate(t3)).isEqualTo("eve");
    }

    @Test
    @DisplayName("revoke of unknown token is a no-op (no exception)")
    void revoke_unknownToken_noException() {
        // Should not throw
        store.revoke("non-existent-token-xyz");
    }
}

