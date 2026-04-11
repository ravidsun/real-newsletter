package com.realnewsletter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configurable rate-limiting properties bound from the {@code rate-limit} prefix.
 *
 * <pre>
 * rate-limit:
 *   enabled: true
 *   limit-for-period: 100            # max requests allowed per refresh window
 *   limit-refresh-period-seconds: 60 # length of one window in seconds
 *   timeout-duration-millis: 0       # 0 = fail fast (HTTP 429 immediately)
 *   path-patterns:
 *     - /api/**
 * </pre>
 *
 * These map directly to Resilience4j {@code RateLimiterConfig} fields, so
 * they can be changed per-environment in {@code application-dev.yml} etc.
 */
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    /** Master switch – set to false to disable rate limiting entirely. */
    private boolean enabled = true;

    /**
     * Maximum number of calls allowed during one {@link #limitRefreshPeriodSeconds refresh period}.
     * This is the Resilience4j {@code limitForPeriod}.
     */
    private int limitForPeriod = 100;

    /**
     * Duration of one sliding window, in seconds.
     * The limiter resets the counter after every period.
     * This is the Resilience4j {@code limitRefreshPeriod}.
     */
    private long limitRefreshPeriodSeconds = 60;

    /**
     * How long (in milliseconds) a request may wait for a permit before being
     * rejected with HTTP 429. Set to {@code 0} for immediate rejection.
     * This is the Resilience4j {@code timeoutDuration}.
     */
    private long timeoutDurationMillis = 0;

    /** URL patterns to which the rate limit is applied. */
    private List<String> pathPatterns = List.of("/api/**");

    // ----- getters & setters -----

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getLimitForPeriod() { return limitForPeriod; }
    public void setLimitForPeriod(int limitForPeriod) { this.limitForPeriod = limitForPeriod; }

    public long getLimitRefreshPeriodSeconds() { return limitRefreshPeriodSeconds; }
    public void setLimitRefreshPeriodSeconds(long limitRefreshPeriodSeconds) {
        this.limitRefreshPeriodSeconds = limitRefreshPeriodSeconds;
    }

    public long getTimeoutDurationMillis() { return timeoutDurationMillis; }
    public void setTimeoutDurationMillis(long timeoutDurationMillis) {
        this.timeoutDurationMillis = timeoutDurationMillis;
    }

    public List<String> getPathPatterns() { return pathPatterns; }
    public void setPathPatterns(List<String> pathPatterns) { this.pathPatterns = pathPatterns; }
}
