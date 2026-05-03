package com.realnewsletter.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

/**
 * Base JPA entity for articles. Uses SINGLE_TABLE inheritance so that
 * articles from different news sources share the same database table.
 * The {@code source_type} discriminator column identifies the source.
 *
 * <p>Note: Sealed classes are incompatible with Hibernate's proxy-based lazy loading.
 * The class is declared {@code abstract} to prevent direct instantiation.
 * Only {@link NewsdataArticle} and {@link NewsApiArticle} are concrete subtypes.</p>
 */
@Entity
@Table(name = "articles")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "source_type", discriminatorType = DiscriminatorType.STRING)
public abstract class Article {

    @Id
    @GeneratedValue
    private UUID id;

    /** Auto-incrementing sequence number assigned by the database on insert. */
    @Generated(event = EventType.INSERT)
    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

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

    /**
     * SHA-256 hex of the normalized title (lowercased, non-alphanumeric chars removed).
     * Used for cross-source duplicate detection. Null when title is absent.
     */
    @Column(name = "title_hash", length = 64, unique = true)
    private String titleHash;

    /**
     * Set by Hibernate before INSERT; never updated afterwards.
     * Using @CreationTimestamp instead of DB DEFAULT + insertable=false ensures
     * the value is always present in the Java entity immediately after save.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /**
     * Set by Hibernate before both INSERT and UPDATE.
     * Using @UpdateTimestamp instead of DB DEFAULT + insertable=false ensures
     * the value is always present in the Java entity immediately after save/update.
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
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

    public Long getSeq() { return seq; }

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

    public String getTitleHash() { return titleHash; }
    public void setTitleHash(String titleHash) { this.titleHash = titleHash; }

    /**
     * Computes a SHA-256 hex digest of the normalized title so that the same story
     * published by different sources (with different URLs) can be detected as a duplicate.
     *
     * <p>Normalization: lowercase → strip every character that is not a letter, digit,
     * or space → collapse/trim whitespace.</p>
     *
     * @param title raw article title; returns {@code null} when blank
     */
    public static String computeTitleHash(String title) {
        if (title == null || title.isBlank()) return null;
        String normalized = title.toLowerCase()
                .replaceAll("[^a-z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isEmpty()) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
