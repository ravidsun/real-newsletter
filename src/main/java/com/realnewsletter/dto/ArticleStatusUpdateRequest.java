package com.realnewsletter.dto;

import com.realnewsletter.model.ArticleStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PUT /api/v1/articles/{id}}.
 * Carries the new lifecycle status for the target article.
 */
public record ArticleStatusUpdateRequest(@NotNull ArticleStatus status) {}

