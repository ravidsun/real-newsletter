package com.realnewsletter.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers rate-limiting interceptors:
 * <ol>
 *   <li>{@link RateLimitInterceptorImpl} (Resilience4j) – broad coverage of
 *       all {@code /api/**} paths (configured via {@link RateLimitProperties}).</li>
 *   <li>{@link Bucket4jRateLimitInterceptor} (Bucket4j) – strict per-IP
 *       token-bucket limiting specifically on {@code /api/auth/**} and
 *       {@code /api/v1/search} (configured via {@link Bucket4jRateLimitProperties}).</li>
 * </ol>
 */
@Configuration
@EnableConfigurationProperties({RateLimitProperties.class, Bucket4jRateLimitProperties.class})
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimitInterceptorImpl rateLimitInterceptor;
    private final RateLimitProperties props;
    private final Bucket4jRateLimitInterceptor bucket4jInterceptor;

    public RateLimitConfig(RateLimitInterceptorImpl rateLimitInterceptor,
                           RateLimitProperties props,
                           Bucket4jRateLimitInterceptor bucket4jInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.props = props;
        this.bucket4jInterceptor = bucket4jInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. Resilience4j broad rate limiter on all /api/** paths
        String[] patterns = props.getPathPatterns().toArray(new String[0]);
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns(patterns);

        // 2. Bucket4j strict rate limiter on auth and search endpoints
        registry.addInterceptor(bucket4jInterceptor)
                .addPathPatterns("/api/auth/**", "/api/v1/search");
    }
}
