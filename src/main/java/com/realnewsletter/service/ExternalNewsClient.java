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
 * Service for fetching articles from external news API.
 */
@Service
public class ExternalNewsClient {

    private static final Logger logger = LoggerFactory.getLogger(ExternalNewsClient.class);

    private final WebClient webClient;

    @Value("${external.news.api.url:https://newsapi.org/v2/top-headlines}")
    private String trendingApiUrl;

    @Value("${external.news.api.key:}")
    private String apiKey;

    public ExternalNewsClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Fetches trending articles from the external API.
     * @return Flux of ArticleDto
     */
    public Flux<ArticleDto> fetchTrendingArticles() {
        return webClient.get()
            .uri(trendingApiUrl + "?apiKey=" + apiKey + "&country=us")
            .retrieve()
            .onStatus(status -> !status.is2xxSuccessful(), response -> {
                logger.error("Error fetching articles: {}", response.statusCode());
                return Mono.error(new RuntimeException("Failed to fetch articles"));
            })
            .bodyToMono(NewsApiResponse.class)
            .flatMapMany(response -> Flux.fromIterable(response.articles()))
            .map(article -> new ArticleDto(article.url(), article.title(), article.content()));
    }

    /**
     * Wrapper for NewsAPI response.
     */
    public record NewsApiResponse(List<Article> articles) {}

    /**
     * Represents an article from NewsAPI.
     */
    public record Article(String url, String title, String content) {}
}
