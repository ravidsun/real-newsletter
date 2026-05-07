package com.realnewsletter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration driven entirely by {@code cors.*} application properties.
 *
 * <p>Set {@code CORS_ALLOWED_ORIGINS} as an environment variable (e.g. on Render)
 * to restrict origins in production without touching code:
 * <pre>
 *   CORS_ALLOWED_ORIGINS=https://my-frontend.vercel.app,https://other-origin.com
 * </pre>
 * Defaults to {@code *} (all origins) when the variable is absent.
 */
@Configuration
public class CorsConfig {

    /** Comma-separated list of allowed origins. Supports wildcard {@code *}. */
    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String allowedMethods;

    @Value("${cors.max-age:3600}")
    private long maxAge;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                String[] origins = allowedOrigins.split(",");
                String[] methods = allowedMethods.split(",");

                registry.addMapping("/api/**")
                        // allowedOriginPatterns supports both "*" and specific URLs
                        // and is compatible with allowCredentials(true)
                        .allowedOriginPatterns(origins)
                        .allowedMethods(methods)
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(maxAge);
            }
        };
    }
}
