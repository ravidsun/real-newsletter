package com.realnewsletter.service;

import com.realnewsletter.model.NewsdataArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Client for fetching articles from the Newsdata.io API (https://newsdata.io).
 */
@Service
public class ExternalNewsClient {

    private static final Logger logger = LoggerFactory.getLogger(ExternalNewsClient.class);

    private final WebClient webClient;

    @Value("${external.newsdata.url:https://newsdata.io/api/1/news}")
    private String articlesApiUrl;

    @Value("${external.newsdata.key:}")
    private String apiKey;

    public ExternalNewsClient(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Fetches the latest trending articles from the Newsdata.io API.
     *
     * @return Flux of {@link NewsdataArticle}
     */
    public Flux<NewsdataArticle> fetchTrendingArticles() {
        return webClient.get()
            .uri(articlesApiUrl + "?apikey=" + apiKey + "&language=en&country=us")
            .retrieve()
            .onStatus(status -> !status.is2xxSuccessful(), response -> {
                logger.error("Error fetching articles from Newsdata.io: {}", response.statusCode());
                return Mono.error(new RuntimeException("Failed to fetch articles from Newsdata.io"));
            })
            .bodyToMono(NewsdataResponse.class)
            .flatMapMany(response -> Flux.fromIterable(response.results()))
            .take(2)
            .map(this::mapToArticle);
    }

    /**
     * Fetches one page of articles from Newsdata.io using optional filter parameters.
     *
     * @param country   ISO 3166-1 alpha-2 country code (may be {@code null} to omit)
     * @param language  BCP-47 language code (may be {@code null} to omit)
     * @param category  news category (may be {@code null} to omit)
     * @param nextPage  pagination cursor returned by the previous call; {@code null} for first page
     * @param pageSize  number of results to request (Newsdata.io: {@code size} parameter)
     * @return the raw API response including {@code nextPage} cursor and article list
     */
    public Mono<NewsdataResponse> fetchPage(String country, String language,
                                            String category, String nextPage, int pageSize) {
        StringBuilder url = new StringBuilder(articlesApiUrl)
                .append("?apikey=").append(apiKey)
                .append("&size=").append(pageSize);
        if (country  != null && !country.isBlank())  url.append("&country=").append(country);
        if (language != null && !language.isBlank()) url.append("&language=").append(language);
        if (category != null && !category.isBlank()) url.append("&category=").append(category);
        if (nextPage != null && !nextPage.isBlank())  url.append("&page=").append(nextPage);

        return webClient.get()
                .uri(url.toString())
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), response -> {
                    logger.error("Error fetching page from Newsdata.io: {}", response.statusCode());
                    return Mono.error(new RuntimeException("Failed to fetch page from Newsdata.io"));
                })
                .bodyToMono(NewsdataResponse.class);
    }

    /** Maps a raw Newsdata.io article record to a {@link NewsdataArticle} entity. */
    public NewsdataArticle mapToArticle(NewsdataArticleRaw a) {
        NewsdataArticle article = new NewsdataArticle(a.link(), a.title(),
                a.content() != null ? a.content() : a.description());
        article.setArticleId(a.article_id());
        article.setDescription(a.description());
        article.setKeywords(a.keywordsAsString());
        article.setCreator(a.creatorAsString());
        article.setLanguage(a.language());
        article.setCountry(a.countryAsString());
        article.setCategory(a.categoryAsString());
        article.setImageUrl(a.image_url());
        article.setVideoUrl(a.video_url());
        article.setSourceId(a.source_id());
        article.setSourceName(a.source_name());
        article.setSourcePriority(a.source_priority());
        article.setSourceUrl(a.source_url());
        article.setSourceIcon(a.source_icon());
        article.setSentiment(a.sentiment());
        article.setSentimentStats(a.sentiment_stats());
        article.setPubDateTz(a.pubDateTZ());
        article.setDuplicate(false);
        return article;
    }

    /** Top-level Newsdata.io API response wrapper. */
    public record NewsdataResponse(String status, List<NewsdataArticleRaw> results, String nextPage) {}

    /**
     * Raw Newsdata.io article. Array-type fields are converted to comma-separated strings.
     */
    public record NewsdataArticleRaw(
            String article_id,
            String title,
            String link,
            String description,
            String content,
            List<String> keywords,
            List<String> creator,
            String language,
            List<String> country,
            List<String> category,
            String image_url,
            String video_url,
            String pubDate,
            String pubDateTZ,
            String fetched_at,
            String source_id,
            String source_name,
            Integer source_priority,
            String source_url,
            String source_icon,
            String sentiment,
            String sentiment_stats
    ) {
        String keywordsAsString() { return keywords != null ? String.join(",", keywords) : null; }
        String creatorAsString()  { return creator  != null ? String.join(",", creator)  : null; }
        String countryAsString()  { return country  != null ? String.join(",", country)  : null; }
        String categoryAsString() { return category != null ? String.join(",", category) : null; }
    }
}
