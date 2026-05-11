package com.realnewsletter.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtService}.
 */
class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties props;

    @BeforeEach
    void setUp() {
        props = new JwtProperties();
        props.setSecret("unit-test-secret-key-that-is-long-enough-for-hs256!");
        props.setAccessTokenTtlMinutes(15);
        jwtService = new JwtService(props);
    }

    @Test
    @DisplayName("generateAccessToken produces a parseable token with correct subject")
    void generateAccessToken_setsSubjectCorrectly() {
        String token = jwtService.generateAccessToken("alice", "ROLE_ADMIN");
        Claims claims = jwtService.parseToken(token);
        assertThat(claims.getSubject()).isEqualTo("alice");
    }

    @Test
    @DisplayName("generateAccessToken embeds roles claim")
    void generateAccessToken_setsRolesCorrectly() {
        String token = jwtService.generateAccessToken("bob", "ROLE_USER,ROLE_ADMIN");
        String roles = jwtService.extractRoles(token);
        assertThat(roles).isEqualTo("ROLE_USER,ROLE_ADMIN");
    }

    @Test
    @DisplayName("isTokenValid returns true for a freshly generated token")
    void isTokenValid_freshToken_returnsTrue() {
        String token = jwtService.generateAccessToken("charlie", "ROLE_USER");
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid returns false for a tampered token")
    void isTokenValid_tamperedToken_returnsFalse() {
        String token = jwtService.generateAccessToken("dave", "ROLE_USER");
        String tampered = token.substring(0, token.length() - 4) + "XXXX";
        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("parseToken throws JwtException for an invalid token")
    void parseToken_invalidToken_throwsJwtException() {
        assertThatThrownBy(() -> jwtService.parseToken("not.a.jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("extractSubject returns null for an invalid token")
    void extractSubject_invalidToken_returnsNull() {
        assertThat(jwtService.extractSubject("garbage")).isNull();
    }

    @Test
    @DisplayName("extractSubject returns correct subject for a valid token")
    void extractSubject_validToken_returnsSubject() {
        String token = jwtService.generateAccessToken("eve", "ROLE_ADMIN");
        assertThat(jwtService.extractSubject(token)).isEqualTo("eve");
    }

    @Test
    @DisplayName("Token generated with different secret fails validation")
    void token_wrongSecret_failsValidation() {
        JwtProperties otherProps = new JwtProperties();
        otherProps.setSecret("completely-different-secret-key-32chars!!");
        JwtService otherService = new JwtService(otherProps);

        String tokenFromOther = otherService.generateAccessToken("frank", "ROLE_USER");
        assertThat(jwtService.isTokenValid(tokenFromOther)).isFalse();
    }
}

