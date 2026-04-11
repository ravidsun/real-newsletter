package com.realnewsletter.dto;

import java.util.UUID;

/**
 * DTO representing an article from external news API.
 */
public record ArticleDto(UUID id, String url, String title, String content) {
}
