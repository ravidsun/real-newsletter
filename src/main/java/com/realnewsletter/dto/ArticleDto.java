package com.realnewsletter.dto;

import com.realnewsletter.model.Article;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing an article from Newsdata.io API.
 */
public record ArticleDto(
    UUID id,
    String articleId,
    String link,
    String title,
    String description,
    String content,
    String keywords,
    String creator,
    String language,
    String country,
    String category,
    String datatype,
    Instant pubDate,
    String pubDateTz,
    Instant fetchedAt,
    String imageUrl,
    String videoUrl,
    String sourceId,
    String sourceName,
    Integer sourcePriority,
    String sourceUrl,
    String sourceIcon,
    String sentiment,
    String sentimentStats,
    String aiTag,
    String aiRegion,
    String aiOrg,
    String aiSummary,
    Boolean duplicate,
    Instant createdAt
) {

    /**
     * Converts an Article entity to ArticleDto.
     */
    public static ArticleDto fromEntity(Article article) {
        return new ArticleDto(
            article.getId(),
            article.getArticleId(),
            article.getLink(),
            article.getTitle(),
            article.getDescription(),
            article.getContent(),
            article.getKeywords(),
            article.getCreator(),
            article.getLanguage(),
            article.getCountry(),
            article.getCategory(),
            article.getDatatype(),
            article.getPubDate(),
            article.getPubDateTz(),
            article.getFetchedAt(),
            article.getImageUrl(),
            article.getVideoUrl(),
            article.getSourceId(),
            article.getSourceName(),
            article.getSourcePriority(),
            article.getSourceUrl(),
            article.getSourceIcon(),
            article.getSentiment(),
            article.getSentimentStats(),
            article.getAiTag(),
            article.getAiRegion(),
            article.getAiOrg(),
            article.getAiSummary(),
            article.getDuplicate(),
            article.getCreatedAt()
        );
    }

    /**
     * Converts ArticleDto to Article entity.
     */
    public static Article toEntity(ArticleDto dto) {
        Article article = new Article(dto.link(), dto.title(), dto.content());
        article.setArticleId(dto.articleId());
        article.setDescription(dto.description());
        article.setKeywords(dto.keywords());
        article.setCreator(dto.creator());
        article.setLanguage(dto.language());
        article.setCountry(dto.country());
        article.setCategory(dto.category());
        article.setDatatype(dto.datatype());
        article.setPubDate(dto.pubDate());
        article.setPubDateTz(dto.pubDateTz());
        article.setFetchedAt(dto.fetchedAt());
        article.setImageUrl(dto.imageUrl());
        article.setVideoUrl(dto.videoUrl());
        article.setSourceId(dto.sourceId());
        article.setSourceName(dto.sourceName());
        article.setSourcePriority(dto.sourcePriority());
        article.setSourceUrl(dto.sourceUrl());
        article.setSourceIcon(dto.sourceIcon());
        article.setSentiment(dto.sentiment());
        article.setSentimentStats(dto.sentimentStats());
        article.setAiTag(dto.aiTag());
        article.setAiRegion(dto.aiRegion());
        article.setAiOrg(dto.aiOrg());
        article.setAiSummary(dto.aiSummary());
        article.setDuplicate(dto.duplicate());
        return article;
    }
}
