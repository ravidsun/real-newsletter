package com.realnewsletter.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Article sourced from the Newsdata.io API.
 * Extends the base {@link Article} with Newsdata-specific fields.
 */
@Entity
@DiscriminatorValue("NEWSDATA")
public class NewsdataArticle extends Article {

    @Column(name = "article_id", columnDefinition = "TEXT")
    private String articleId;

    @Column(columnDefinition = "TEXT")
    private String keywords;

    @Column(columnDefinition = "TEXT")
    private String language;

    @Column(columnDefinition = "TEXT")
    private String country;

    @Column(columnDefinition = "TEXT")
    private String category;

    @Column(columnDefinition = "TEXT")
    private String datatype;

    @Column(name = "pub_date_tz")
    private String pubDateTz;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @Column(name = "video_url", columnDefinition = "TEXT")
    private String videoUrl;

    @Column(name = "source_priority")
    private Integer sourcePriority;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "source_icon", columnDefinition = "TEXT")
    private String sourceIcon;

    @Column(columnDefinition = "TEXT")
    private String sentiment;

    @Column(name = "sentiment_stats", columnDefinition = "TEXT")
    private String sentimentStats;

    @Column(name = "ai_region", columnDefinition = "TEXT")
    private String aiRegion;

    @Column(name = "ai_org", columnDefinition = "TEXT")
    private String aiOrg;

    @Column(name = "is_duplicate")
    private Boolean duplicate;

    public NewsdataArticle() {}

    public NewsdataArticle(String link, String title, String content) {
        super(link, title, content);
    }

    public String getArticleId() { return articleId; }
    public void setArticleId(String articleId) { this.articleId = articleId; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDatatype() { return datatype; }
    public void setDatatype(String datatype) { this.datatype = datatype; }

    public String getPubDateTz() { return pubDateTz; }
    public void setPubDateTz(String pubDateTz) { this.pubDateTz = pubDateTz; }

    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public Integer getSourcePriority() { return sourcePriority; }
    public void setSourcePriority(Integer sourcePriority) { this.sourcePriority = sourcePriority; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public String getSourceIcon() { return sourceIcon; }
    public void setSourceIcon(String sourceIcon) { this.sourceIcon = sourceIcon; }

    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }

    public String getSentimentStats() { return sentimentStats; }
    public void setSentimentStats(String sentimentStats) { this.sentimentStats = sentimentStats; }

    public String getAiRegion() { return aiRegion; }
    public void setAiRegion(String aiRegion) { this.aiRegion = aiRegion; }

    public String getAiOrg() { return aiOrg; }
    public void setAiOrg(String aiOrg) { this.aiOrg = aiOrg; }

    public Boolean getDuplicate() { return duplicate; }
    public void setDuplicate(Boolean duplicate) { this.duplicate = duplicate; }
}

