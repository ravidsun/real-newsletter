package com.realnewsletter.config;

import com.realnewsletter.auth.JwtAuthenticationFilter;
import com.realnewsletter.auth.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration for the Real Newsletter API.
 *
 * <p>Key security measures configured here:
 * <ul>
 *   <li><b>CORS</b> – {@link CorsConfigurationSource} bean restricts allowed origins
 *       to the Angular frontend origin(s) declared in {@code cors.allowed-origins}.
 *       Requests from unlisted origins are rejected at preflight.</li>
 *   <li><b>CSRF</b> – disabled for the stateless REST API.</li>
 *   <li><b>JWT</b> – {@link JwtAuthenticationFilter} reads Bearer tokens from the
 *       {@code Authorization} header and sets the SecurityContext accordingly.</li>
 *   <li><b>RBAC</b> – all URL-level requests are technically permitted, but individual
 *       state-changing controller methods carry {@code @PreAuthorize("hasRole('ADMIN')")}
 *       annotations enforced by {@link EnableMethodSecurity}. Both anonymous and
 *       authenticated-but-not-ADMIN access returns HTTP 403.</li>
 *   <li><b>Session</b> – {@code STATELESS} – no HTTP session is created or used.</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Comma-separated list of allowed CORS origins; driven by the
     * {@code cors.allowed-origins} application property (env: {@code CORS_ALLOWED_ORIGINS}).
     */
    @Value("${cors.allowed-origins:http://localhost:4201}")
    private String allowedOrigins;

    @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String allowedMethods;

    @Value("${cors.max-age:3600}")
    private long maxAge;

    /** Admin username for the in-memory UserDetailsService. */
    @Value("${auth.admin.username:admin}")
    private String adminUsername;

    /** BCrypt-encoded admin password. Override via {@code AUTH_ADMIN_PASSWORD_HASH} env var. */
    @Value("${auth.admin.password-hash:#{null}}")
    private String adminPasswordHash;

    /** Plain-text admin password fallback for dev/test environments. */
    @Value("${auth.admin.password:admin}")
    private String adminPassword;

    /**
     * Main security filter chain.
     *
     * <ul>
     *   <li>CORS is applied via the {@link CorsConfigurationSource} bean.</li>
     *   <li>CSRF is disabled – the API is stateless (no session / cookie auth).</li>
     *   <li>{@code /api/auth/**} is publicly accessible (login, refresh, logout).</li>
     *   <li>All other URL patterns are permitted at the URL level; per-method RBAC is
     *       enforced via {@code @PreAuthorize} annotations and method-level security.</li>
     *   <li>Both {@code AuthenticationEntryPoint} and {@code AccessDeniedHandler} return
     *       HTTP 403 so that callers consistently receive 403 regardless of whether
     *       they are unauthenticated or authenticated without the required role.</li>
     *   <li>HTTP Basic is available so that integration tests can supply credentials.</li>
     *   <li>{@link JwtAuthenticationFilter} runs before the standard username/password filter.</li>
     * </ul>
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
            // ── CORS ──────────────────────────────────────────────────────────────────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── CSRF: disabled for stateless REST API ───────────────────────────────
            .csrf(AbstractHttpConfigurer::disable)

            // ── Session: no HTTP session (JWT / token-based or test mock users) ─────
            .sessionManagement(sm ->
                    sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── URL-level authorisation: permit all (method-level @PreAuthorize used) ─
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/api/auth/**").permitAll()
                    .anyRequest().permitAll()
            )

            // ── Exception handling: always return 403 (not redirect to login) ────────
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, authEx) ->
                        res.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied"))
                .accessDeniedHandler((req, res, accEx) ->
                        res.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied"))
            )

            // ── HTTP Basic: allows test clients to supply credentials ─────────────────
            .httpBasic(basic -> {})

            // ── JWT filter: validates Bearer tokens from Authorization header ─────────
            .addFilterBefore(new JwtAuthenticationFilter(jwtService),
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * {@link CorsConfigurationSource} bean used by the Spring Security CORS filter.
     *
     * <p>This is the authoritative CORS configuration; the {@link CorsConfig}
     * WebMvc bean is aligned to the same origin list so both layers are consistent.</p>
     *
     * <p>Requests from origins not listed in {@code cors.allowed-origins} will be
     * rejected at preflight with an empty / blocked response.</p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Specific origins only (wildcard "*" is incompatible with allowCredentials=true)
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        config.setAllowedOriginPatterns(origins);

        List<String> methods = Arrays.asList(allowedMethods.split(","));
        config.setAllowedMethods(methods);

        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * BCrypt password encoder used for hashing and verifying passwords.
     *
     * @return a {@link BCryptPasswordEncoder} with default strength (10 rounds)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * In-memory {@link UserDetailsService} backed by a single configurable admin user.
     *
     * <p>In production, set {@code auth.admin.password-hash} to a BCrypt hash of the
     * admin password. In development, {@code auth.admin.password} (plain-text) is used
     * and encoded at startup.</p>
     *
     * @param passwordEncoder the encoder used to encode the plain-text fallback password
     * @return configured {@link InMemoryUserDetailsManager}
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        String encodedPassword;
        if (adminPasswordHash != null && !adminPasswordHash.isBlank()) {
            // Production: use pre-hashed BCrypt password
            encodedPassword = adminPasswordHash;
        } else {
            // Dev/test fallback: encode the plain-text password at startup
            encodedPassword = passwordEncoder.encode(adminPassword);
        }

        var adminUser = User.withUsername(adminUsername)
                .password(encodedPassword)
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(adminUser);
    }

    /**
     * Exposes the {@link AuthenticationManager} as a Spring bean so it can be
     * injected into the {@link com.realnewsletter.auth.AuthController}.
     *
     * @param config the Spring Security {@link AuthenticationConfiguration}
     * @return the configured {@link AuthenticationManager}
     * @throws Exception if the manager cannot be retrieved
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
