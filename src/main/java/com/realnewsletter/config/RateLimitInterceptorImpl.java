package com.realnewsletter.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * Servlet interceptor that enforces per-IP rate limits using Resilience4j's
 * {@link RateLimiter}.
 *
 * <p>A {@link RateLimiterRegistry} backed by the configuration from
 * {@link RateLimitProperties} is used to lazily create one {@link RateLimiter}
 * per client IP address. When the limit is exhausted the interceptor returns
 * HTTP 429 Too Many Requests with a JSON body and standard rate-limit headers.
 *
 * <p>All parameters ({@code limitForPeriod}, {@code limitRefreshPeriod},
 * {@code timeoutDuration}, path patterns) are driven by {@link RateLimitProperties}
 * so they can be changed in {@code application.yml} per environment.
 */
@Component
public class RateLimitInterceptorImpl implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptorImpl.class);

    private final RateLimitProperties props;

    /**
     * Registry that creates and caches one {@link RateLimiter} per IP.
     * The default config applied to every new limiter is built from {@link RateLimitProperties}.
     */
    private final RateLimiterRegistry registry;

    public RateLimitInterceptorImpl(RateLimitProperties props) {
        this.props = props;
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(props.getLimitForPeriod())
                .limitRefreshPeriod(Duration.ofSeconds(props.getLimitRefreshPeriodSeconds()))
                .timeoutDuration(Duration.ofMillis(props.getTimeoutDurationMillis()))
                .build();
        this.registry = RateLimiterRegistry.of(config);
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        if (!props.isEnabled()) {
            return true;
        }

        String ip = resolveClientIp(request);
        // rateLimiter() creates a new limiter with the default config on first call per IP
        RateLimiter limiter = registry.rateLimiter(ip);

        try {
            // With timeoutDuration = 0 this throws RequestNotPermitted immediately when
            // the window is exhausted, so no thread blocking occurs.
            limiter.acquirePermission();

            int remaining = Math.max(0, limiter.getMetrics().getAvailablePermissions());
            response.setHeader("X-RateLimit-Limit",     String.valueOf(props.getLimitForPeriod()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            return true;

        } catch (RequestNotPermitted e) {
            log.warn("Rate limit exceeded for IP: {}", ip);

            long retryAfter = props.getLimitRefreshPeriodSeconds();
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("X-RateLimit-Limit",     String.valueOf(props.getLimitForPeriod()));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("Retry-After",            String.valueOf(retryAfter));
            response.getWriter().write(
                    ("{\"error\":\"Too Many Requests\"," +
                     "\"message\":\"Rate limit exceeded. Max %d requests per %d seconds.\"," +
                     "\"retryAfterSeconds\":%d}").formatted(
                            props.getLimitForPeriod(), retryAfter, retryAfter));
            return false;
        }
    }

    /**
     * Extracts the real client IP, honouring {@code X-Forwarded-For} so that
     * the limiter works correctly behind a reverse proxy or load balancer.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
