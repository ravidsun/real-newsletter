package com.realnewsletter.service;

import com.realnewsletter.client.ExternalNewsClient;
import com.realnewsletter.dto.ArticleDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service responsible for periodically fetching articles from the external
 * news API and preparing them for downstream processing (persistence,
 * summarisation, etc.).
 *
 * <p>In this initial implementation the ingested articles are only logged.
 * Persistence will be wired in a subsequent issue.</p>
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final ExternalNewsClient externalNewsClient;

    /**
     * Constructs the service with its dependency injected via constructor.
     *
     * @param externalNewsClient client used to fetch trending articles
     */
    public IngestionService(ExternalNewsClient externalNewsClient) {
        this.externalNewsClient = externalNewsClient;
    }

    /**
     * Scheduled ingestion job that runs at a configurable fixed delay.
     *
     * <p>The delay defaults to 600,000 ms (10 minutes) and can be overridden
     * via the {@code ingestion.interval.ms} application property.</p>
     *
     * <p>The method blocks the scheduler thread intentionally using
     * {@code .block()} because Spring's {@code @Scheduled} infrastructure
     * runs tasks on a dedicated thread pool that is separate from the Netty
     * event-loop; blocking here does not affect the reactive HTTP layer.</p>
     */
    @Scheduled(fixedDelayString = "${ingestion.interval.ms:600000}")
    public void ingestScheduled() {
        log.info("Ingestion job started – fetching trending articles");

        List<ArticleDto> articles = externalNewsClient.fetchTrendingArticles()
                .collectList()
                .block();

        int count = articles == null ? 0 : articles.size();
        log.info("Ingestion job completed – fetched {} article(s)", count);

        // TODO (issue #4): persist articles to the database.
    }
}

