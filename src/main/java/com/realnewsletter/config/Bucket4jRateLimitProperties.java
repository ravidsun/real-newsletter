package com.realnewsletter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties for the Bucket4j-based strict rate limiter applied to
 * {@code /api/auth/**} and {@code /api/v1/search} endpoints.
 *
 * <pre>
 * bucket4j-rate-limit:
 *   enabled: true
 *   capacity: 20              # burst capacity (token bucket size)
 *   refill-tokens: 20         # tokens added per refill window
 *   refill-period-seconds: 60 # length of one refill window in seconds
 * </pre>
 */
@ConfigurationProperties(prefix = "bucket4j-rate-limit")
public class Bucket4jRateLimitProperties {

    /** Master switch – set {@code false} to disable entirely (useful in tests). */
    private boolean enabled = true;

    /** Maximum burst capacity – total tokens the bucket can hold. */
    private long capacity = 20;

    /** Number of tokens added to the bucket per refill window. */
    private long refillTokens = 20;

    /** Duration of one refill window in seconds. */
    private long refillPeriodSeconds = 60;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public long getCapacity() { return capacity; }
    public void setCapacity(long capacity) { this.capacity = capacity; }

    public long getRefillTokens() { return refillTokens; }
    public void setRefillTokens(long refillTokens) { this.refillTokens = refillTokens; }

    public long getRefillPeriodSeconds() { return refillPeriodSeconds; }
    public void setRefillPeriodSeconds(long refillPeriodSeconds) {
        this.refillPeriodSeconds = refillPeriodSeconds;
    }
}

