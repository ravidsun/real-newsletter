package com.realnewsletter.service;

import com.realnewsletter.model.NewsApiArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

/**
 * Client for fetching articles from the NewsAPI.org API
 * (https://newsapi.org/v2/top-headlines).
 */
@Service
public class NewsApiClient {

    private static final Logger logger = LoggerFactory.getLogger(NewsApiClient.class);

    private final WebClient webClient;

    @Value("${external.newsapi.url:https://newsapi.org/v2/top-headlines}")
    private String newsApiUrl;

    @Value("${external.newsapi.key:}")
    private String newsApiKey;

    public NewsApiClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Fetches top headlines from NewsAPI.org.
     *
     * @return Flux of {@link NewsApiArticle}
     */
    public Flux<NewsApiArticle> fetchTopHeadlines() {
        if (newsApiKey == null || newsApiKey.isBlank()) {
            logger.warn("NewsAPI key not configured, skipping NewsAPI ingestion");
            return Flux.empty();
        }
        return webClient.get()
                .uri(newsApiUrl + "?country=us&apiKey=" + newsApiKey)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), response -> {
                    logger.error("Error fetching articles from NewsAPI: {}", response.statusCode());
                    return Mono.error(new RuntimeException("Failed to fetch articles from NewsAPI"));
                })
                .bodyToMono(NewsApiResponse.class)
                .flatMapMany(response -> Flux.fromIterable(response.articles()))
                .take(2)
                .map(this::mapToArticle);
    }

    /**
     * Fetches one page of top headlines from NewsAPI.org with optional filters.
     *
     * @param country   ISO 3166-1 alpha-2 country code (may be {@code null} to omit)
     * @param language  BCP-47 language code (may be {@code null} to omit)
     * @param category  news category (may be {@code null} to omit)
     * @param page      1-based page number (1 = first page)
     * @param pageSize  number of results to request
     * @return the raw API response including totalResults and articles list
     */
    public Mono<NewsApiResponse> fetchPage(String country, String language,
                                           String category, int page, int pageSize) {
        if (newsApiKey == null || newsApiKey.isBlank()) {
            logger.warn("NewsAPI key not configured – returning empty response");
            return Mono.just(new NewsApiResponse("ok", 0, List.of()));
        }
        StringBuilder url = new StringBuilder(newsApiUrl)
                .append("?apiKey=").append(newsApiKey)
                .append("&page=").append(page)
                .append("&pageSize=").append(pageSize);
        if (country  != null && !country.isBlank())  url.append("&country=").append(country);
        if (language != null && !language.isBlank()) url.append("&language=").append(language);
        if (category != null && !category.isBlank()) url.append("&category=").append(category);

        return webClient.get()
                .uri(url.toString())
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), response -> {
                    logger.error("Error fetching page {} from NewsAPI: {}", page, response.statusCode());
                    return Mono.error(new RuntimeException("Failed to fetch page from NewsAPI"));
                })
                .bodyToMono(NewsApiResponse.class);
    }

    /** Maps a raw NewsAPI article record to a {@link NewsApiArticle} entity. */
    public NewsApiArticle mapToArticle(NewsApiArticleRaw a) {
        NewsApiArticle article = new NewsApiArticle(a.url(), a.title(), a.content());
        article.setDescription(a.description());
        article.setCreator(a.author());
        article.setImageUrl(a.urlToImage());
        article.setSourceId(a.source() != null ? a.source().id() : null);
        article.setSourceName(a.source() != null ? a.source().name() : null);
        if (a.publishedAt() != null) {
            try {
                article.setPubDate(Instant.parse(a.publishedAt()));
            } catch (Exception e) {
                logger.warn("Could not parse publishedAt: {}", a.publishedAt());
            }
        }
        return article;
    }

    /** Top-level NewsAPI response wrapper. */
    public record NewsApiResponse(String status, Integer totalResults, List<NewsApiArticleRaw> articles) {}

    /** Represents a single article returned by NewsAPI. */
    public record NewsApiArticleRaw(
            NewsApiSource source,
            String author,
            String title,
            String description,
            String url,
            String urlToImage,
            String publishedAt,
            String content
    ) {}

    /** Represents the source object in a NewsAPI article. */
    public record NewsApiSource(String id, String name) {}
}

