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
            .map(a -> {
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
            });
    }

    /** Top-level Newsdata.io API response wrapper. */
    public record NewsdataResponse(String status, List<NewsdataArticleRaw> results) {}

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
