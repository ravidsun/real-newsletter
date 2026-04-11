package com.realnewsletter.dto;

import com.realnewsletter.model.Article;

/**
 * DTO representing an article from external news API.
 */
public record ArticleDto(String url, String title, String content) {

    /**
     * Converts an Article entity to ArticleDto.
     */
    public static ArticleDto fromEntity(Article article) {
        return new ArticleDto(
            article.getUrl(),
            article.getTitle(),
            article.getContent()
        );
    }

    /**
     * Converts ArticleDto to Article entity.
     */
    public static Article toEntity(ArticleDto dto) {
        Article article = new Article(dto.url(), dto.title(), dto.content());
        return article;
    }
}
