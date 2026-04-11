package com.realnewsletter.dto;

import java.util.UUID;

/**
 * Data Transfer Object representing a single news article returned by the
 * external news API.
 *
 * <p>Fields are intentionally nullable so that partial API responses do not
 * cause deserialization failures; callers are responsible for validating
 * required fields before persisting or processing an article.</p>
 */
public class ArticleDto {

    /** Locally assigned identifier; may be {@code null} when the DTO is first created from API data. */
    private UUID id;

    /** Canonical URL of the article. */
    private String url;

    /** Human-readable headline of the article. */
    private String title;

    /** Full or truncated body text of the article. */
    private String content;

    /** No-arg constructor required by Jackson for deserialization. */
    public ArticleDto() {
    }

    /**
     * Convenience all-args constructor.
     *
     * @param id      article identifier
     * @param url     article URL
     * @param title   article headline
     * @param content article body text
     */
    public ArticleDto(UUID id, String url, String title, String content) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.content = content;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

