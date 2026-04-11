package com.realnewsletter.dto;

import com.realnewsletter.model.Article;
import com.realnewsletter.model.NewsdataArticle;

import java.time.Instant;
import java.util.UUID;

/**
 * Unified read DTO for articles from any news source.
 * Source-specific fields are null when not applicable.
 */
public record ArticleDto(
    UUID id,
    String sourceType,
    String link,
    String title,
    String description,
    String content,
    String creator,
    String sourceId,
    String sourceName,
    String imageUrl,
    Instant pubDate,
    String aiSummary,
    String aiTag,
    Instant createdAt,
    // Newsdata-specific (null for NewsAPI articles)
    String articleId,
    String keywords,
    String language,
    String country,
    String category,
    String datatype,
    String pubDateTz,
    Instant fetchedAt,
    String videoUrl,
    Integer sourcePriority,
    String sourceUrl,
    String sourceIcon,
    String sentiment,
    String sentimentStats,
    String aiRegion,
    String aiOrg,
    Boolean duplicate
) {

    /**
     * Converts any Article subtype to ArticleDto.
     * Newsdata-specific fields are populated only for {@link NewsdataArticle}.
     */
    public static ArticleDto fromEntity(Article article) {
        String articleId = null, keywords = null, language = null, country = null,
               category = null, datatype = null, pubDateTz = null, videoUrl = null,
               sourceUrl = null, sourceIcon = null, sentiment = null,
               sentimentStats = null, aiRegion = null, aiOrg = null;
        Integer sourcePriority = null;
        Instant fetchedAt = null;
        Boolean duplicate = null;

        if (article instanceof NewsdataArticle nd) {
            articleId      = nd.getArticleId();
            keywords       = nd.getKeywords();
            language       = nd.getLanguage();
            country        = nd.getCountry();
            category       = nd.getCategory();
            datatype       = nd.getDatatype();
            pubDateTz      = nd.getPubDateTz();
            fetchedAt      = nd.getFetchedAt();
            videoUrl       = nd.getVideoUrl();
            sourcePriority = nd.getSourcePriority();
            sourceUrl      = nd.getSourceUrl();
            sourceIcon     = nd.getSourceIcon();
            sentiment      = nd.getSentiment();
            sentimentStats = nd.getSentimentStats();
            aiRegion       = nd.getAiRegion();
            aiOrg          = nd.getAiOrg();
            duplicate      = nd.getDuplicate();
        }

        // Infer source type from discriminator value via class name
        String sourceType = article instanceof NewsdataArticle ? "NEWSDATA" : "NEWSAPI";

        return new ArticleDto(
            article.getId(), sourceType,
            article.getLink(), article.getTitle(), article.getDescription(), article.getContent(),
            article.getCreator(), article.getSourceId(), article.getSourceName(), article.getImageUrl(),
            article.getPubDate(), article.getAiSummary(), article.getAiTag(), article.getCreatedAt(),
            articleId, keywords, language, country, category, datatype,
            pubDateTz, fetchedAt, videoUrl, sourcePriority, sourceUrl, sourceIcon,
            sentiment, sentimentStats, aiRegion, aiOrg, duplicate
        );
    }
}
