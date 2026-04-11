package com.realnewsletter.service;

import com.realnewsletter.dto.ArticleDto;
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

    public IngestionService(ExternalNewsClient externalNewsClient) {
        this.externalNewsClient = externalNewsClient;
    }

    /**
     * Scheduled method to ingest trending articles.
     */
    @Scheduled(fixedDelayString = "${ingestion.interval.ms:600000}")
    public void ingestScheduled() {
        logger.info("Starting scheduled ingestion");
        List<ArticleDto> articleList = externalNewsClient.fetchTrendingArticles().collectList().block();
        logger.info("Fetched {} articles", articleList.size());
        // For now, just log (persistence will be added in issue #4)
    }
}
