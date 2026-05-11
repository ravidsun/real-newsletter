package com.realnewsletter.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new article via {@code POST /api/v1/articles}.
 *
 * <p>Rich-text fields ({@code title}, {@code description}, {@code content}) are
 * sanitized by {@link com.realnewsletter.service.HtmlSanitizerService} before
 * the article is persisted.</p>
 */
public record ArticleCreateRequest(

        /** Article URL – must be unique within the system. */
        @NotBlank(message = "link must not be blank")
        String link,

        /** Article headline; HTML will be sanitized before persistence. */
        String title,

        /** Short summary / teaser; HTML will be sanitized before persistence. */
        String description,

        /** Full article body; HTML will be sanitized before persistence. */
        String content,

        /** Optional author name or by-line. */
        String creator
) {}

