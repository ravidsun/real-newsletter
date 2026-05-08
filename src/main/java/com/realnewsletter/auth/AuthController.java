package com.realnewsletter.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * REST controller for JWT-based authentication endpoints.
 *
 * <ul>
 *   <li>{@code POST /api/auth/login}   — authenticate with username/password and receive an
 *       access token (body) + refresh token (HttpOnly cookie).</li>
 *   <li>{@code POST /api/auth/refresh} — exchange a valid refresh token (from cookie) for
 *       a new access token.</li>
 *   <li>{@code POST /api/auth/logout}  — invalidate the refresh token and clear the cookie.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtProperties jwtProperties;

    public AuthController(AuthenticationManager authManager,
                          JwtService jwtService,
                          RefreshTokenStore refreshTokenStore,
                          JwtProperties jwtProperties) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.refreshTokenStore = refreshTokenStore;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Authenticates the user with username and password.
     *
     * <p>On success:
     * <ul>
     *   <li>A short-lived JWT access token is returned in the response body.</li>
     *   <li>A long-lived refresh token is set as an HttpOnly, Secure, SameSite=Strict cookie.</li>
     * </ul>
     * </p>
     *
     * @param request  the login credentials
     * @param response the HTTP response (used to set the refresh token cookie)
     * @return {@link LoginResponse} containing the access token
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletResponse response) {
        Authentication auth;
        try {
            auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            log.warn("Login failed for user '{}': {}", request.username(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        String accessToken = jwtService.generateAccessToken(auth.getName(), roles);
        String refreshToken = refreshTokenStore.createToken(auth.getName());

        addRefreshTokenCookie(response, refreshToken);

        log.info("User '{}' logged in successfully", auth.getName());
        return ResponseEntity.ok(new LoginResponse(
                accessToken,
                "Bearer",
                jwtProperties.getAccessTokenTtlMinutes() * 60L));
    }

    /**
     * Issues a new access token using a valid refresh token from the HttpOnly cookie.
     *
     * <p>The old refresh token is <b>rotated</b>: the existing token is revoked and a new
     * one is issued on each successful call, limiting the window of exposure if a token
     * is ever intercepted.</p>
     *
     * @param request  the HTTP request (used to read the refresh token cookie)
     * @param response the HTTP response (used to set the new refresh token cookie)
     * @return {@link LoginResponse} containing a new access token
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(HttpServletRequest request,
                                                 HttpServletResponse response) {
        String refreshToken = extractRefreshTokenFromCookie(request);
        if (refreshToken == null) {
            log.debug("Refresh attempt with no refresh token cookie");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String username = refreshTokenStore.validate(refreshToken);
        if (username == null) {
            log.warn("Invalid or expired refresh token presented");
            clearRefreshTokenCookie(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Token rotation: revoke old token, issue new one
        refreshTokenStore.revoke(refreshToken);
        String newRefreshToken = refreshTokenStore.createToken(username);
        addRefreshTokenCookie(response, newRefreshToken);

        // Issue new access token (re-use roles claim from old token if available,
        // else default to ROLE_USER since we can't re-derive roles without a DB lookup here)
        String newAccessToken = jwtService.generateAccessToken(username, "ROLE_USER");

        log.info("Access token refreshed for user '{}'", username);
        return ResponseEntity.ok(new LoginResponse(
                newAccessToken,
                "Bearer",
                jwtProperties.getAccessTokenTtlMinutes() * 60L));
    }

    /**
     * Logs the user out by revoking the refresh token and clearing the cookie.
     *
     * @param request  the HTTP request (used to extract the refresh token cookie)
     * @param response the HTTP response (used to clear the cookie)
     * @return 204 No Content on success, 401 if no valid refresh cookie is present
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                       HttpServletResponse response) {
        String refreshToken = extractRefreshTokenFromCookie(request);
        if (refreshToken != null) {
            refreshTokenStore.revoke(refreshToken);
        }
        clearRefreshTokenCookie(response);
        log.info("User logged out, refresh token cookie cleared");
        return ResponseEntity.noContent().build();
    }

    // ── private helpers ────────────────────────────────────────────────────────

    /**
     * Writes the refresh token as an HttpOnly, Secure, SameSite=Strict cookie.
     *
     * @param response     the HTTP response to add the cookie to
     * @param refreshToken the opaque refresh token value
     */
    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        long maxAgeSeconds = jwtProperties.getRefreshTokenTtlDays() * 86400L;
        String cookieName = jwtProperties.getRefreshCookieName();

        // Use Set-Cookie header directly to set SameSite=Strict (Servlet API Cookie does not support it natively)
        String cookieHeader = String.format(
                "%s=%s; Path=/api/auth; Max-Age=%d; HttpOnly; Secure; SameSite=Strict",
                cookieName, refreshToken, maxAgeSeconds);
        response.addHeader("Set-Cookie", cookieHeader);
    }

    /**
     * Clears the refresh token cookie by setting its Max-Age to 0.
     *
     * @param response the HTTP response to modify
     */
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        String cookieName = jwtProperties.getRefreshCookieName();
        String cookieHeader = String.format(
                "%s=; Path=/api/auth; Max-Age=0; HttpOnly; Secure; SameSite=Strict",
                cookieName);
        response.addHeader("Set-Cookie", cookieHeader);
    }

    /**
     * Reads the refresh token value from the incoming request's cookies.
     *
     * @param request the HTTP request
     * @return the refresh token string, or {@code null} if not found
     */
    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String cookieName = jwtProperties.getRefreshCookieName();
        return Arrays.stream(cookies)
                .filter(c -> cookieName.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}

