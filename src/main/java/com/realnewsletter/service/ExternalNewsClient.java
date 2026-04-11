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
 * Service for fetching articles from the Newsdata.io API
 * (https://newsdata.io).
 */
@Service
public class ExternalNewsClient {

    private static final Logger logger = LoggerFactory.getLogger(ExternalNewsClient.class);

    private final WebClient webClient;

    @Value("${external.news.api.url:https://newsdata.io/api/1/news}")
    private String articlesApiUrl;

    @Value("${external.news.api.key:}")
    private String apiKey;

    public ExternalNewsClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Fetches the latest articles from the Newsdata.io API.
     *
     * <p>Query parameters:
     * <ul>
     *   <li>{@code apikey}   – Newsdata.io API key (lowercase 'k')</li>
     *   <li>{@code language} – filter to English articles</li>
     *   <li>{@code country}  – filter to US sources</li>
     * </ul>
     *
     * @return Flux of {@link ArticleDto}
     */
    public Flux<ArticleDto> fetchTrendingArticles() {
        return webClient.get()
            .uri(articlesApiUrl + "?apikey=" + apiKey + "&language=en&country=us")
            .retrieve()
            .onStatus(status -> !status.is2xxSuccessful(), response -> {
                logger.error("Error fetching articles from Newsdata.io: {}", response.statusCode());
                return Mono.error(new RuntimeException("Failed to fetch articles from Newsdata.io"));
            })
            .bodyToMono(NewsdataResponse.class)
            .flatMapMany(response -> Flux.fromIterable(response.results()))
            .map(article -> new ArticleDto(
                    null,
                    article.link(),                                      // canonical source URL
                    article.title(),
                    article.content() != null ? article.content()        // full body when available
                                              : article.description(),   // fall back to lead description
                    null,
                    null,
                    null));
    }

    /**
     * Top-level Newsdata.io API response wrapper.
     * The {@code results} array contains the articles.
     */
    public record NewsdataResponse(String status, List<NewsdataArticle> results) {}

    /**
     * Represents a single article returned by the Newsdata.io API.
     *
     * <ul>
     *   <li>{@code link}        – canonical source URL</li>
     *   <li>{@code description} – short lead paragraph (always present)</li>
     *   <li>{@code content}     – full article body (may be {@code null} on the free tier)</li>
     *   <li>{@code pubDate}     – publication date string, e.g. {@code "2026-04-11 09:00:00"}</li>
     * </ul>
     */
    public record NewsdataArticle(
            String title,
            String link,
            String description,
            String content,
            String pubDate
    ) {}
}
