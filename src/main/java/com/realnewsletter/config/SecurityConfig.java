package com.realnewsletter.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
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

    /**
     * Main security filter chain.
     *
     * <ul>
     *   <li>CORS is applied via the {@link CorsConfigurationSource} bean.</li>
     *   <li>CSRF is disabled – the API is stateless (no session / cookie auth).</li>
     *   <li>All URL patterns are permitted at the URL level; per-method RBAC is
     *       enforced via {@code @PreAuthorize} annotations and method-level security.</li>
     *   <li>Both {@code AuthenticationEntryPoint} and {@code AccessDeniedHandler} return
     *       HTTP 403 so that callers consistently receive 403 regardless of whether
     *       they are unauthenticated or authenticated without the required role.</li>
     *   <li>HTTP Basic is available so that integration tests can supply credentials.</li>
     * </ul>
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
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
            .httpBasic(basic -> {});

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
}

