package com.realnewsletter.scheduler;

import com.realnewsletter.config.NewsDataSchedulerProperties;
import com.realnewsletter.model.NewArticleEvent;
import com.realnewsletter.model.NewsdataArticle;
import com.realnewsletter.repository.ArticleRepository;
import com.realnewsletter.service.AiEnhancementService;
import com.realnewsletter.service.ExternalNewsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled bulk-ingestion job for the Newsdata.io API.
 *
 * <p>Runs according to the cron expression in {@code scheduler.newsdata.cron} (default: 6 AM daily)
 * and performs up to {@code scheduler.newsdata.max-requests} paginated API calls per run.
 * Each fetched article is persisted to the database; duplicates are silently skipped.</p>
 *
 * <p>The job can be disabled entirely by setting {@code scheduler.newsdata.enabled: false}
 * in {@code application.yml} or the active profile's config file.</p>
 */
@Component
public class NewsDataIngestionScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NewsDataIngestionScheduler.class);

    private final NewsDataSchedulerProperties props;
    private final ExternalNewsClient newsClient;
    private final ArticleRepository articleRepository;
    private final AiEnhancementService aiEnhancementService;
    private final ApplicationEventPublisher eventPublisher;

    public NewsDataIngestionScheduler(NewsDataSchedulerProperties props,
                                      ExternalNewsClient newsClient,
                                      ArticleRepository articleRepository,
                                      AiEnhancementService aiEnhancementService,
                                      ApplicationEventPublisher eventPublisher) {
        this.props = props;
        this.newsClient = newsClient;
        this.articleRepository = articleRepository;
        this.aiEnhancementService = aiEnhancementService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Entry point triggered by Spring's scheduling infrastructure.
     * The cron expression is read from {@code scheduler.newsdata.cron}.
     */
    @Scheduled(cron = "${scheduler.newsdata.cron:0 0 6 * * *}")
    public void runIngestion() {
        if (!props.isEnabled()) {
            logger.info("[NewsDataScheduler] Scheduler is disabled – skipping run.");
            return;
        }

        logger.info("[NewsDataScheduler] Starting bulk ingestion from Newsdata.io "
                + "(maxRequests={}, pageSize={}, country={}, language={}, category={})",
                props.getMaxRequests(), props.getPageSize(),
                props.getCountry(), props.getLanguage(), props.getCategory());

        int totalFetched = 0;
        int totalSaved   = 0;
        int totalSkipped = 0;
        int errors       = 0;
        String nextPage  = null;

        for (int request = 1; request <= props.getMaxRequests(); request++) {
            try {
                ExternalNewsClient.NewsdataResponse response = newsClient.fetchPage(
                        props.getCountry(), props.getLanguage(),
                        props.getCategory(), nextPage, props.getPageSize()
                ).block();

                if (response == null || response.results() == null || response.results().isEmpty()) {
                    logger.info("[NewsDataScheduler] No more results on request #{} – stopping early.", request);
                    break;
                }

                List<NewsdataArticle> articles = new ArrayList<>();
                for (ExternalNewsClient.NewsdataArticleRaw raw : response.results()) {
                    articles.add(newsClient.mapToArticle(raw));
                }
                totalFetched += articles.size();

                for (NewsdataArticle article : articles) {
                    if (articleRepository.existsByLink(article.getLink())) {
                        totalSkipped++;
                    } else {
                        try {
                            aiEnhancementService.enrichArticle(article);
                        } catch (Exception e) {
                            logger.warn("[NewsDataScheduler] AI enrichment failed for {}, saving without AI data",
                                    article.getLink(), e);
                        }
                        articleRepository.save(article);
                        eventPublisher.publishEvent(new NewArticleEvent(article));
                        totalSaved++;
                    }
                }

                nextPage = response.nextPage();
                if (nextPage == null || nextPage.isBlank()) {
                    logger.info("[NewsDataScheduler] Reached last page on request #{} – stopping.", request);
                    break;
                }

            } catch (Exception e) {
                errors++;
                logger.error("[NewsDataScheduler] Error on request #{}: {}", request, e.getMessage(), e);
                break; // stop on error to avoid hammering a failing API
            }
        }

        logger.info("[NewsDataScheduler] Run complete – fetched={}, saved={}, duplicatesSkipped={}, errors={}",
                totalFetched, totalSaved, totalSkipped, errors);
    }
}
