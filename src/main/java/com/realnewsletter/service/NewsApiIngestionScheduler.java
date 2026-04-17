package com.realnewsletter.service;

import com.realnewsletter.config.NewsApiSchedulerProperties;
import com.realnewsletter.model.NewsApiArticle;
import com.realnewsletter.repository.ArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled bulk-ingestion job for the NewsAPI.org API.
 *
 * <p>Runs according to the cron expression in {@code scheduler.newsapi.cron} (default: 7 AM daily)
 * and performs up to {@code scheduler.newsapi.max-requests} paginated API calls per run.
 * Each fetched article is persisted to the database; duplicates are silently skipped.</p>
 *
 * <p>The job can be disabled entirely by setting {@code scheduler.newsapi.enabled: false}
 * in {@code application.yml} or the active profile's config file.</p>
 */
@Component
public class NewsApiIngestionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NewsApiIngestionScheduler.class);

    private final NewsApiSchedulerProperties props;
    private final NewsApiClient newsApiClient;
    private final ArticleRepository articleRepository;

    public NewsApiIngestionScheduler(NewsApiSchedulerProperties props,
                                     NewsApiClient newsApiClient,
                                     ArticleRepository articleRepository) {
        this.props = props;
        this.newsApiClient = newsApiClient;
        this.articleRepository = articleRepository;
    }

    /**
     * Entry point triggered by Spring's scheduling infrastructure.
     * The cron expression is read from {@code scheduler.newsapi.cron}.
     */
    @Scheduled(cron = "${scheduler.newsapi.cron:0 0 7 * * *}")
    public void runIngestion() {
        if (!props.isEnabled()) {
            logger.info("[NewsApiScheduler] Scheduler is disabled – skipping run.");
            return;
        }

        logger.info("[NewsApiScheduler] Starting bulk ingestion from NewsAPI.org "
                + "(maxRequests={}, pageSize={}, country={}, language={}, category={})",
                props.getMaxRequests(), props.getPageSize(),
                props.getCountry(), props.getLanguage(), props.getCategory());

        int totalFetched = 0;
        int totalSaved   = 0;
        int totalSkipped = 0;
        int errors       = 0;

        for (int page = 1; page <= props.getMaxRequests(); page++) {
            try {
                NewsApiClient.NewsApiResponse response = newsApiClient.fetchPage(
                        props.getCountry(), props.getLanguage(),
                        props.getCategory(), page, props.getPageSize()
                ).block();

                if (response == null || response.articles() == null || response.articles().isEmpty()) {
                    logger.info("[NewsApiScheduler] No more results on page {} – stopping early.", page);
                    break;
                }

                List<NewsApiArticle> articles = response.articles().stream()
                        .map(newsApiClient::mapToArticle)
                        .toList();

                totalFetched += articles.size();

                for (NewsApiArticle article : articles) {
                    if (article.getLink() == null || article.getLink().isBlank()) {
                        totalSkipped++;
                        continue;
                    }
                    if (articleRepository.existsByLink(article.getLink())) {
                        totalSkipped++;
                    } else {
                        articleRepository.save(article);
                        totalSaved++;
                    }
                }

                // Stop when the page returned fewer articles than requested — last page reached
                if (articles.size() < props.getPageSize()) {
                    logger.info("[NewsApiScheduler] Partial page ({} articles) on page {} – stopping.",
                            articles.size(), page);
                    break;
                }

                // Stop when total results exhausted
                int totalResults = response.totalResults() != null ? response.totalResults() : 0;
                if (totalFetched >= totalResults) {
                    logger.info("[NewsApiScheduler] All {} total results fetched – stopping.", totalResults);
                    break;
                }

            } catch (Exception e) {
                errors++;
                logger.error("[NewsApiScheduler] Error on page {}: {}", page, e.getMessage(), e);
                break; // stop on error to avoid hammering a failing API
            }
        }

        logger.info("[NewsApiScheduler] Run complete – fetched={}, saved={}, duplicatesSkipped={}, errors={}",
                totalFetched, totalSaved, totalSkipped, errors);
    }
}

