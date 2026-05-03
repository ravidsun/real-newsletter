package com.realnewsletter.scheduler;

import com.realnewsletter.config.NewsApiSchedulerProperties;
import com.realnewsletter.model.NewArticleEvent;
import com.realnewsletter.model.NewsApiArticle;
import com.realnewsletter.repository.ArticleRepository;
import com.realnewsletter.service.AiEnhancementService;
import com.realnewsletter.service.NewsApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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
    private final AiEnhancementService aiEnhancementService;
    private final ApplicationEventPublisher eventPublisher;

    public NewsApiIngestionScheduler(NewsApiSchedulerProperties props,
                                     NewsApiClient newsApiClient,
                                     ArticleRepository articleRepository,
                                     AiEnhancementService aiEnhancementService,
                                     ApplicationEventPublisher eventPublisher) {
        this.props = props;
        this.newsApiClient = newsApiClient;
        this.articleRepository = articleRepository;
        this.aiEnhancementService = aiEnhancementService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Scheduled entry point — respects the {@code scheduler.newsapi.enabled} flag.
     * For manual/on-demand runs use {@link #runIngestionNow()} directly.
     */
    @Scheduled(cron = "${scheduler.newsapi.cron:0 0 7 * * *}")
    public void runIngestion() {
        if (!props.isEnabled()) {
            logger.info("[NewsApiScheduler] Scheduler is disabled – skipping run.");
            return;
        }
        runIngestionNow();
    }

    /**
     * Executes the ingestion unconditionally (bypasses the enabled flag).
     * Called by {@link com.realnewsletter.controller.IngestionController} for manual triggers.
     *
     * @return {@link IngestionResult} containing counts of fetched/saved/skipped/errored articles
     */
    public IngestionResult runIngestionNow() {

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
                    if ("rate-limited".equals(response != null ? response.status() : null)) {
                        logger.warn("[NewsApiScheduler] Rate limited by NewsAPI on page {} – stopping pagination.", page);
                    } else {
                        logger.info("[NewsApiScheduler] No more results on page {} – stopping early.", page);
                    }
                    break;
                }

                List<NewsApiArticle> articles = response.articles().stream()
                        .map(newsApiClient::mapToArticle)
                        .toList();

                totalFetched += articles.size();

                for (NewsApiArticle article : articles) {
                    if (article.getLink() == null || article.getLink().isBlank()) {
                        logger.warn("[NewsApiScheduler] Skipping article with null/blank link: title={}",
                                article.getTitle());
                        totalSkipped++;
                        continue;
                    }
                    if (articleRepository.existsByLink(article.getLink())) {
                        totalSkipped++;
                    } else if (article.getTitleHash() != null
                               && articleRepository.existsByTitleHash(article.getTitleHash())) {
                        logger.debug("[NewsApiScheduler] Skipping cross-source duplicate (title hash match): {}",
                                article.getLink());
                        totalSkipped++;
                    } else {
                        try {
                            aiEnhancementService.enrichArticle(article);
                        } catch (Exception e) {
                            logger.warn("[NewsApiScheduler] AI enrichment failed for {}, saving without AI data",
                                    article.getLink(), e);
                        }
                        try {
                            articleRepository.save(article);
                            eventPublisher.publishEvent(new NewArticleEvent(article));
                            totalSaved++;
                        } catch (Exception e) {
                            logger.error("[NewsApiScheduler] Failed to save article {}: {}",
                                    article.getLink(), e.getMessage(), e);
                            errors++;
                        }
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
        return new IngestionResult(totalFetched, totalSaved, totalSkipped, errors);
    }
}
