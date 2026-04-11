package com.realnewsletter.model;

import jakarta.persistence.*;

/**
 * Article sourced from the NewsAPI.org API.
 * All fields reuse the common columns defined in the base {@link Article}:
 * <ul>
 *   <li>{@code author}       → {@code creator}</li>
 *   <li>{@code urlToImage}   → {@code image_url}</li>
 *   <li>{@code publishedAt}  → {@code pub_date}</li>
 *   <li>{@code source.id}    → {@code source_id}</li>
 *   <li>{@code source.name}  → {@code source_name}</li>
 *   <li>{@code url}          → {@code link}</li>
 * </ul>
 */
@Entity
@DiscriminatorValue("NEWSAPI")
public class NewsApiArticle extends Article {

    public NewsApiArticle() {}

    public NewsApiArticle(String link, String title, String content) {
        super(link, title, content);
    }
}

