package com.realnewsletter.service;

import com.realnewsletter.model.Article;
import com.realnewsletter.model.NewArticleEvent;
import com.realnewsletter.repository.ArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for scheduled ingestion of articles from all configured news sources.
 */
@Service
public class IngestionService {

    private static final Logger logger = LoggerFactory.getLogger(IngestionService.class);

    private final ExternalNewsClient externalNewsClient;
    private final NewsApiClient newsApiClient;
    private final ArticleRepository articleRepository;
    private final AiEnhancementService aiEnhancementService;
    private final ApplicationEventPublisher eventPublisher;

    public IngestionService(ExternalNewsClient externalNewsClient,
                            NewsApiClient newsApiClient,
                            ArticleRepository articleRepository,
                            AiEnhancementService aiEnhancementService,
                            ApplicationEventPublisher eventPublisher) {
        this.externalNewsClient = externalNewsClient;
        this.newsApiClient = newsApiClient;
        this.articleRepository = articleRepository;
        this.aiEnhancementService = aiEnhancementService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Scheduled method to ingest articles from all news sources.
     */
    @Scheduled(fixedDelayString = "${ingestion.interval.ms:600000}")
    public void ingestScheduled() {
        logger.info("Starting scheduled ingestion from all sources");
        ingestFrom("Newsdata.io", fetchNewsdata());
        ingestFrom("NewsAPI",     fetchNewsApi());
    }

    private List<? extends Article> fetchNewsdata() {
        try {
            return externalNewsClient.fetchTrendingArticles().collectList().block();
        } catch (Exception e) {
            logger.error("Error fetching from Newsdata.io", e);
            return List.of();
        }
    }

    private List<? extends Article> fetchNewsApi() {
        try {
            return newsApiClient.fetchTopHeadlines().collectList().block();
        } catch (Exception e) {
            logger.error("Error fetching from NewsAPI", e);
            return List.of();
        }
    }

    private void ingestFrom(String sourceName, List<? extends Article> articles) {
        int duplicates = 0;
        int saved = 0;
        for (Article article : articles) {
            if (articleRepository.existsByLink(article.getLink())) {
                duplicates++;
                logger.debug("Skipping duplicate from {}: {}", sourceName, article.getLink());
            } else {
                try {
                    aiEnhancementService.enrichArticle(article);
                } catch (Exception e) {
                    logger.warn("AI enrichment failed for {}, saving without AI data", article.getLink(), e);
                }
                articleRepository.save(article);
                eventPublisher.publishEvent(new NewArticleEvent(article));
                saved++;
                logger.debug("Saved article from {}: {}", sourceName, article.getLink());
            }
        }
        logger.info("[{}] fetched={}, duplicates={}, saved={}", sourceName, articles.size(), duplicates, saved);
    }
}
