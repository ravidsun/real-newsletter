package com.realnewsletter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable properties for the Newsdata.io bulk-ingestion scheduler.
 *
 * <pre>
 * scheduler:
 *   newsdata:
 *     enabled: true
 *     cron: '0 0 6 * * *'   # 6 AM daily
 *     max-requests: 100      # max paginated API calls per run
 *     page-size: 10          # articles requested per API call
 *     country: us
 *     language: en
 *     category: general
 * </pre>
 */
@ConfigurationProperties(prefix = "scheduler.newsdata")
public class NewsDataSchedulerProperties {

    /** Master switch – set to {@code false} to disable the scheduler entirely. */
    private boolean enabled = true;

    /**
     * Spring cron expression for the trigger time.
     * Default: every day at 06:00 AM (server-local time).
     */
    private String cron = "0 0 6 * * *";

    /**
     * Maximum number of paginated API requests performed in a single run.
     * The scheduler stops early when Newsdata.io returns no {@code nextPage} cursor.
     */
    private int maxRequests = 100;

    /**
     * Number of articles to request per API call.
     * Newsdata.io free-tier supports up to 10 results per page.
     */
    private int pageSize = 10;

    /** ISO 3166-1 alpha-2 country code filter (e.g. {@code us}). */
    private String country = "us";

    /** BCP-47 language code filter (e.g. {@code en}). */
    private String language = "en";

    /** News category filter (e.g. {@code general}, {@code business}, {@code technology}).
     *  Defaults to {@code null} – omitted from the request so all categories are fetched.
     *  Set in application-{profile}.yml only when you need to narrow the results. */
    private String category = null;

    // ----- getters & setters -----

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }

    public int getMaxRequests() { return maxRequests; }
    public void setMaxRequests(int maxRequests) { this.maxRequests = maxRequests; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}

