package com.realnewsletter.service;

import com.realnewsletter.dto.ArticleDto;
import com.realnewsletter.model.Article;
import com.realnewsletter.repository.ArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for scheduled ingestion of articles.
 */
@Service
public class IngestionService {

    private static final Logger logger = LoggerFactory.getLogger(IngestionService.class);

    private final ExternalNewsClient externalNewsClient;
    private final ArticleRepository articleRepository;
    private final AiEnhancementService aiEnhancementService;

    public IngestionService(ExternalNewsClient externalNewsClient,
                            ArticleRepository articleRepository,
                            AiEnhancementService aiEnhancementService) {
        this.externalNewsClient = externalNewsClient;
        this.articleRepository = articleRepository;
        this.aiEnhancementService = aiEnhancementService;
    }

    /**
     * Scheduled method to ingest trending articles.
     */
    @Scheduled(fixedDelayString = "${ingestion.interval.ms:600000}")
    public void ingestScheduled() {
        logger.info("Starting scheduled ingestion");
        List<ArticleDto> articleList;
        try {
            articleList = externalNewsClient.fetchTrendingArticles().collectList().block();
            logger.info("Fetched {} articles", articleList.size());
        } catch (Exception e) {
            logger.error("Error fetching articles", e);
            articleList = List.of();
        }

        int duplicates = 0;
        int saved = 0;
        for (ArticleDto dto : articleList) {
            if (articleRepository.existsByUrl(dto.url())) {
                duplicates++;
                logger.debug("Skipping duplicate article: {}", dto.url());
            } else {
                Article article = ArticleDto.toEntity(dto);
                try {
                    aiEnhancementService.enrichArticle(article);
                } catch (Exception e) {
                    logger.warn("AI enrichment failed for article {}, saving without AI data", dto.url(), e);
                }
                articleRepository.save(article);
                saved++;
                logger.debug("Saved new article: {}", dto.url());
            }
        }
        logger.info("Ingestion complete: fetched={}, duplicates={}, saved={}", articleList.size(), duplicates, saved);
    }
}
