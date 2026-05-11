package com.realnewsletter.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servlet interceptor that enforces strict per-IP rate limits on sensitive endpoints
 * ({@code /api/auth/**} and {@code /api/v1/search}) using the Bucket4j token-bucket
 * algorithm.
 *
 * <p>A dedicated {@link Bucket} is created lazily per client IP address and cached
 * in a {@link ConcurrentHashMap}. When the bucket's tokens are exhausted the
 * interceptor writes HTTP 429 Too Many Requests and blocks the request from
 * reaching the controller.</p>
 *
 * <p>Configuration (driven by {@link Bucket4jRateLimitProperties}):
 * <ul>
 *   <li>{@code capacity} – maximum burst size (default: 20 requests)</li>
 *   <li>{@code refill-tokens} – tokens added per refill window (default: 20)</li>
 *   <li>{@code refill-period-seconds} – refill window length in seconds (default: 60)</li>
 * </ul>
 * </p>
 */
@Component
public class Bucket4jRateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(Bucket4jRateLimitInterceptor.class);

    /** Per-IP bucket cache. In production this would be backed by a distributed cache. */
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private final Bucket4jRateLimitProperties props;

    public Bucket4jRateLimitInterceptor(Bucket4jRateLimitProperties props) {
        this.props = props;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if (!props.isEnabled()) {
            return true;
        }

        String ip = resolveClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(ip, this::newBucket);

        if (bucket.tryConsume(1)) {
            long remaining = bucket.getAvailableTokens();
            response.setHeader("X-RateLimit-Limit",     String.valueOf(props.getCapacity()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            return true;
        }

        log.warn("[Bucket4j] Rate limit exceeded for IP: {} on path: {}", ip, request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-RateLimit-Limit",     String.valueOf(props.getCapacity()));
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setHeader("Retry-After",            String.valueOf(props.getRefillPeriodSeconds()));
        response.getWriter().write(
                ("{\"error\":\"Too Many Requests\"," +
                 "\"message\":\"Rate limit exceeded. Max %d requests per %d seconds.\"," +
                 "\"retryAfterSeconds\":%d}").formatted(
                        props.getCapacity(),
                        props.getRefillPeriodSeconds(),
                        props.getRefillPeriodSeconds()));
        return false;
    }

    /**
     * Creates a new rate-limiting bucket for a client IP using the configured
     * greedy-refill bandwidth.
     *
     * @param ip client IP address (used as key – value not read here)
     * @return a new Bucket with the configured bandwidth policy
     */
    private Bucket newBucket(String ip) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(props.getCapacity())
                .refillGreedy(props.getRefillTokens(),
                              Duration.ofSeconds(props.getRefillPeriodSeconds()))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Resolves the real client IP, honouring {@code X-Forwarded-For} headers set
     * by reverse proxies and load balancers.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

