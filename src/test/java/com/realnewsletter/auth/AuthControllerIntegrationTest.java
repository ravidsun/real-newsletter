package com.realnewsletter.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the JWT authentication flow (issue #33):
 *
 * <ul>
 *   <li>Login with valid credentials returns a JWT access token and HttpOnly refresh cookie.</li>
 *   <li>Login with invalid credentials returns 401.</li>
 *   <li>Refresh with a valid cookie returns a new access token.</li>
 *   <li>Refresh without a cookie returns 401.</li>
 *   <li>Logout clears the refresh token cookie.</li>
 *   <li>Refresh after logout returns 401.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(springSecurity())
                .build();
        // Clear all refresh tokens before each test
        refreshTokenStore.revokeAll("admin");
    }

    // ── Login happy path ───────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login with valid credentials → 200 + access token + refresh cookie")
    void login_validCredentials_returnsAccessTokenAndCookie() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("admin", "admin"));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();

        // Verify refresh token cookie is HttpOnly
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("refresh_token=");
        assertThat(setCookie).containsIgnoringCase("HttpOnly");
        assertThat(setCookie).containsIgnoringCase("SameSite=Strict");
    }

    // ── Login failure ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login with wrong password → 401")
    void login_wrongPassword_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("admin", "wrongpassword"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/login with unknown user → 401")
    void login_unknownUser_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("hacker", "password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ── Refresh happy path ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/refresh with valid cookie → 200 + new access token + rotated cookie")
    void refresh_validCookie_returnsNewAccessTokenAndRotatedCookie() throws Exception {
        // Step 1: Login to obtain refresh token cookie
        String loginBody = objectMapper.writeValueAsString(new LoginRequest("admin", "admin"));
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        String loginAccessToken = objectMapper.readTree(
                loginResult.getResponse().getContentAsString()).get("accessToken").asText();
        String setCookieHeader = loginResult.getResponse().getHeader("Set-Cookie");
        String refreshToken = extractCookieValue(setCookieHeader, "refresh_token");

        // Step 2: Use refresh token to obtain new access token
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        String newAccessToken = objectMapper.readTree(
                refreshResult.getResponse().getContentAsString()).get("accessToken").asText();

        // New access token should be different (different iat)
        assertThat(newAccessToken).isNotNull();

        // Rotated cookie should be present
        String newSetCookie = refreshResult.getResponse().getHeader("Set-Cookie");
        assertThat(newSetCookie).isNotNull();
        assertThat(newSetCookie).contains("refresh_token=");
        assertThat(newSetCookie).containsIgnoringCase("HttpOnly");
    }

    // ── Refresh failure ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/refresh without cookie → 401")
    void refresh_noCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/refresh with invalid token → 401")
    void refresh_invalidToken_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "not-a-real-token")))
                .andExpect(status().isUnauthorized());
    }

    // ── Logout ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/logout clears refresh cookie → 204")
    void logout_clearsCookieAndReturns204() throws Exception {
        // Login first
        String loginBody = objectMapper.writeValueAsString(new LoginRequest("admin", "admin"));
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        String setCookieHeader = loginResult.getResponse().getHeader("Set-Cookie");
        String refreshToken = extractCookieValue(setCookieHeader, "refresh_token");

        // Logout
        MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", refreshToken)))
                .andExpect(status().isNoContent())
                .andReturn();

        // Cookie should be cleared (Max-Age=0)
        String logoutSetCookie = logoutResult.getResponse().getHeader("Set-Cookie");
        assertThat(logoutSetCookie).isNotNull();
        assertThat(logoutSetCookie).containsIgnoringCase("Max-Age=0");
    }

    @Test
    @DisplayName("POST /api/auth/refresh after logout → 401 (token revoked)")
    void refresh_afterLogout_returns401() throws Exception {
        // Login
        String loginBody = objectMapper.writeValueAsString(new LoginRequest("admin", "admin"));
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        String setCookieHeader = loginResult.getResponse().getHeader("Set-Cookie");
        String refreshToken = extractCookieValue(setCookieHeader, "refresh_token");

        // Logout
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", refreshToken)))
                .andExpect(status().isNoContent());

        // Attempt refresh with revoked token
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    // ── Admin role preservation after refresh ──────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/refresh for admin user → new access token preserves ROLE_ADMIN")
    void refresh_adminUser_preservesRoleAdminInNewAccessToken() throws Exception {
        // Step 1: Login as admin
        String loginBody = objectMapper.writeValueAsString(new LoginRequest("admin", "admin"));
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        String loginAccessToken = objectMapper.readTree(
                loginResult.getResponse().getContentAsString()).get("accessToken").asText();
        // Verify the login token itself carries ROLE_ADMIN
        assertThat(jwtService.extractRoles(loginAccessToken)).contains("ROLE_ADMIN");

        String setCookieHeader = loginResult.getResponse().getHeader("Set-Cookie");
        String refreshToken = extractCookieValue(setCookieHeader, "refresh_token");

        // Step 2: Use the refresh token to obtain a new access token
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String newAccessToken = objectMapper.readTree(
                refreshResult.getResponse().getContentAsString()).get("accessToken").asText();

        // Step 3: Verify the refreshed access token still contains ROLE_ADMIN
        String rolesAfterRefresh = jwtService.extractRoles(newAccessToken);
        assertThat(rolesAfterRefresh)
                .as("Refreshed access token must preserve ROLE_ADMIN for admin user")
                .contains("ROLE_ADMIN");
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    /**
     * Extracts a cookie value from a {@code Set-Cookie} header string.
     *
     * @param setCookieHeader the full {@code Set-Cookie} header value
     * @param cookieName      the name of the cookie to extract
     * @return the cookie value
     */
    private String extractCookieValue(String setCookieHeader, String cookieName) {
        assertThat(setCookieHeader).isNotNull();
        String prefix = cookieName + "=";
        int start = setCookieHeader.indexOf(prefix) + prefix.length();
        int end = setCookieHeader.indexOf(';', start);
        return end == -1
                ? setCookieHeader.substring(start)
                : setCookieHeader.substring(start, end);
    }
}

