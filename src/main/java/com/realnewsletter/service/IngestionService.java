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

    public IngestionService(ExternalNewsClient externalNewsClient, ArticleRepository articleRepository) {
        this.externalNewsClient = externalNewsClient;
        this.articleRepository = articleRepository;
    }

    /**
     * Scheduled method to ingest trending articles.
     */
    @Scheduled(fixedDelayString = "${ingestion.interval.ms:600000}")
    public void ingestScheduled() {
        logger.info("Starting scheduled ingestion");
        List<ArticleDto> articleList = externalNewsClient.fetchTrendingArticles().collectList().block();
        logger.info("Fetched {} articles", articleList.size());

        int duplicates = 0;
        int saved = 0;
        for (ArticleDto dto : articleList) {
            if (articleRepository.existsByUrl(dto.url())) {
                duplicates++;
                logger.debug("Skipping duplicate article: {}", dto.url());
            } else {
                Article article = ArticleDto.toEntity(dto);
                articleRepository.save(article);
                saved++;
                logger.debug("Saved new article: {}", dto.url());
            }
        }
        logger.info("Ingestion complete: fetched={}, duplicates={}, saved={}", articleList.size(), duplicates, saved);
    }
}
