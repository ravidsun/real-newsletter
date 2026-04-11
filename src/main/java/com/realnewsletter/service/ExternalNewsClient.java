package com.realnewsletter.service;

import com.realnewsletter.dto.ArticleDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service for fetching articles from the Finlight financial news API
 * (https://finlight.me).
 */
@Service
public class ExternalNewsClient {

    private static final Logger logger = LoggerFactory.getLogger(ExternalNewsClient.class);

    private final WebClient webClient;

    @Value("${external.news.api.url:https://api.finlight.me/v2/articles}")
    private String articlesApiUrl;

    @Value("${external.news.api.key:}")
    private String apiKey;

    public ExternalNewsClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Fetches the latest articles from the Finlight API.
     *
     * <p>Query parameters:
     * <ul>
     *   <li>{@code apiKey}  – Finlight API key</li>
     *   <li>{@code language} – filter to English articles</li>
     *   <li>{@code pageSize} – number of articles per request (max 100)</li>
     * </ul>
     *
     * @return Flux of {@link ArticleDto}
     */
    public Flux<ArticleDto> fetchTrendingArticles() {
        return webClient.get()
            .uri(articlesApiUrl + "?apiKey=" + apiKey + "&language=en&pageSize=20")
            .retrieve()
            .onStatus(status -> !status.is2xxSuccessful(), response -> {
                logger.error("Error fetching articles from Finlight: {}", response.statusCode());
                return Mono.error(new RuntimeException("Failed to fetch articles from Finlight"));
            })
            .bodyToMono(FinlightResponse.class)
            .flatMapMany(response -> Flux.fromIterable(response.articles()))
            .map(article -> new ArticleDto(
                    null,
                    article.link(),                                    // Finlight uses "link" for the source URL
                    article.title(),
                    article.content() != null ? article.content()      // full body when available
                                              : article.description(), // fall back to short description
                    null,
                    null,
                    null));
    }

    /**
     * Top-level Finlight API response wrapper.
     */
    public record FinlightResponse(List<FinlightArticle> articles) {}

    /**
     * Represents a single article returned by the Finlight API.
     * Finlight uses {@code link} for the canonical URL, {@code description}
     * for a short lead paragraph, and {@code content} for the full body (may be null).
     */
    public record FinlightArticle(
            String title,
            String link,
            String description,
            String content,
            String publishedAt
    ) {}
}
