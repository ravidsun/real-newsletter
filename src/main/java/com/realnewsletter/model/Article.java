package com.realnewsletter.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Base JPA entity for articles. Uses SINGLE_TABLE inheritance so that
 * articles from different news sources share the same database table.
 * The {@code source_type} discriminator column identifies the source.
 */
@Entity
@Table(name = "articles")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "source_type", discriminatorType = DiscriminatorType.STRING)
public abstract class Article {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(unique = true, nullable = false, columnDefinition = "TEXT")
    private String link;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String content;

    /** Author/creator of the article (author in NewsAPI, creator[] in Newsdata). */
    @Column(columnDefinition = "TEXT")
    private String creator;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "source_name")
    private String sourceName;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "pub_date")
    private Instant pubDate;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "ai_tag", columnDefinition = "TEXT")
    private String aiTag;

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

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getCreator() { return creator; }
    public void setCreator(String creator) { this.creator = creator; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Instant getPubDate() { return pubDate; }
    public void setPubDate(Instant pubDate) { this.pubDate = pubDate; }

    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }

    public String getAiTag() { return aiTag; }
    public void setAiTag(String aiTag) { this.aiTag = aiTag; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
