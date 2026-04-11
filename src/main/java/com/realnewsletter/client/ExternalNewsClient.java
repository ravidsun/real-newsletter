package com.realnewsletter.client;

import com.realnewsletter.dto.ArticleDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

/**
 * HTTP client responsible for fetching trending articles from the external
 * news API (e.g. <a href="https://newsapi.org">NewsAPI</a>).
 *
 * <p>All network calls are non-blocking and return reactive types so that
 * callers can compose further reactive pipelines without blocking threads.</p>
 */
@Service
public class ExternalNewsClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalNewsClient.class);

    private final WebClient webClient;
    private final String trendingApiUrl;
    private final String apiKey;

    /**
     * Constructs the client with its dependencies injected via constructor.
     *
     * @param webClient      the shared WebClient bean configured with timeouts
     * @param trendingApiUrl base URL of the trending-articles endpoint
     * @param apiKey         API key; may be empty when running in test environments
     */
    public ExternalNewsClient(
            WebClient webClient,
            @Value("${external.news.api.url:https://newsapi.org/v2/top-headlines}") String trendingApiUrl,
            @Value("${external.news.api.key:}") String apiKey) {
        this.webClient = webClient;
        this.trendingApiUrl = trendingApiUrl;
        this.apiKey = apiKey;
    }

    /**
     * Fetches trending articles from the remote news API.
     *
     * <p>The API returns a JSON envelope of the form:
     * <pre>{@code { "articles": [ { "url": "...", "title": "...", "content": "..." } ] }}</pre>
     * The response is unwrapped and each article is emitted individually as an
     * {@link ArticleDto}.</p>
     *
     * <p>Non-2xx HTTP responses are converted to exceptions via
     * {@code onStatus()} so that callers and the reactive pipeline can handle
     * errors uniformly.</p>
     *
     * @return a {@link Flux} emitting one {@link ArticleDto} per article
     */
    public Flux<ArticleDto> fetchTrendingArticles() {
        log.debug("Fetching trending articles from {}", trendingApiUrl);

        URI requestUri = UriComponentsBuilder.fromHttpUrl(trendingApiUrl)
                .queryParam("apiKey", apiKey)
                .build()
                .toUri();

        return webClient.get()
                .uri(requestUri)
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        response -> {
                            log.error("News API returned error status: {}", response.statusCode());
                            return response.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(
                                            new ExternalNewsApiException(
                                                    "News API error " + response.statusCode() + ": " + body)));
                        })
                .bodyToMono(NewsApiResponse.class)
                .flatMapMany(response -> {
                    List<ArticleDto> articles = response.getArticles();
                    if (articles == null || articles.isEmpty()) {
                        log.debug("News API returned no articles");
                        return Flux.empty();
                    }
                    return Flux.fromIterable(articles);
                });
    }

    // ── Inner response wrapper ────────────────────────────────────────────────

    /**
     * Jackson-compatible wrapper that models the top-level NewsAPI JSON response.
     *
     * <pre>{@code { "status": "ok", "totalResults": 5, "articles": [ … ] }}</pre>
     */
    public static class NewsApiResponse {

        private String status;
        private Integer totalResults;
        private List<ArticleDto> articles;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getTotalResults() {
            return totalResults;
        }

        public void setTotalResults(Integer totalResults) {
            this.totalResults = totalResults;
        }

        public List<ArticleDto> getArticles() {
            return articles;
        }

        public void setArticles(List<ArticleDto> articles) {
            this.articles = articles;
        }
    }

    /**
     * Exception thrown when the external news API returns a non-2xx response.
     */
    public static class ExternalNewsApiException extends RuntimeException {

        public ExternalNewsApiException(String message) {
            super(message);
        }
    }
}
