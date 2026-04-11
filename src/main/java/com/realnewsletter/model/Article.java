package com.realnewsletter.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an article from Newsdata.io API.
 */
@Entity
@Table(name = "articles")
public class Article {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "article_id", columnDefinition = "TEXT")
    private String articleId;

    @Column(unique = true, nullable = false, columnDefinition = "TEXT")
    private String link;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String keywords;

    @Column(columnDefinition = "TEXT")
    private String creator;

    @Column(columnDefinition = "TEXT")
    private String language;

    @Column(columnDefinition = "TEXT")
    private String country;

    @Column(columnDefinition = "TEXT")
    private String category;

    @Column(columnDefinition = "TEXT")
    private String datatype;

    @Column(name = "pub_date")
    private Instant pubDate;

    @Column(name = "pub_date_tz")
    private String pubDateTz;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "video_url", columnDefinition = "TEXT")
    private String videoUrl;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "source_name")
    private String sourceName;

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

    @Column(name = "ai_tag", columnDefinition = "TEXT")
    private String aiTag;

    @Column(name = "ai_region", columnDefinition = "TEXT")
    private String aiRegion;

    @Column(name = "ai_org", columnDefinition = "TEXT")
    private String aiOrg;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "is_duplicate")
    private Boolean duplicate;

    @Column(name = "created_at", updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false)
    private Instant updatedAt;

    public Article() {}

    public Article(String link, String title, String content) {
        this.link = link;
        this.title = title;
        this.content = content;
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getArticleId() { return articleId; }
    public void setArticleId(String articleId) { this.articleId = articleId; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDatatype() { return datatype; }
    public void setDatatype(String datatype) { this.datatype = datatype; }

    public Instant getPubDate() { return pubDate; }
    public void setPubDate(Instant pubDate) { this.pubDate = pubDate; }

    public String getPubDateTz() { return pubDateTz; }
    public void setPubDateTz(String pubDateTz) { this.pubDateTz = pubDateTz; }

    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

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

    public String getAiTag() { return aiTag; }
    public void setAiTag(String aiTag) { this.aiTag = aiTag; }

    public String getAiRegion() { return aiRegion; }
    public void setAiRegion(String aiRegion) { this.aiRegion = aiRegion; }

    public String getAiOrg() { return aiOrg; }
    public void setAiOrg(String aiOrg) { this.aiOrg = aiOrg; }

    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }

    public Boolean getDuplicate() { return duplicate; }
    public void setDuplicate(Boolean duplicate) { this.duplicate = duplicate; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
