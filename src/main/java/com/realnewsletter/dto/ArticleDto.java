package com.realnewsletter.dto;

import com.realnewsletter.model.Article;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing an article from external news API.
 */
public record ArticleDto(UUID id, String url, String title, String content,
                         String aiSummary, String tags, Instant createdAt) {

    /**
     * Converts an Article entity to ArticleDto.
     */
    public static ArticleDto fromEntity(Article article) {
        return new ArticleDto(
            article.getId(),
            article.getUrl(),
            article.getTitle(),
            article.getContent(),
            article.getAiSummary(),
            article.getTags(),
            article.getCreatedAt()
        );
    }

    /**
     * Converts ArticleDto to Article entity.
     */
    public static Article toEntity(ArticleDto dto) {
        return new Article(dto.url(), dto.title(), dto.content());
    }
}
