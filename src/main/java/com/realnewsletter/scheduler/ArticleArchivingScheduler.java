package com.realnewsletter.scheduler;

import com.realnewsletter.model.ArticleStatus;
import com.realnewsletter.repository.ArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled daily job that automatically archives articles older than 7 days.
 *
 * <p>Runs at midnight server time every day ({@code 0 0 0 * * *}).
 * Any article with {@code status = PUBLISHED} and {@code pub_date < (now - 7 days)}
 * is moved to {@code ARCHIVED}.  Archived articles disappear from the public feed
 * ({@code GET /api/v1/articles}) but remain accessible via
 * {@code GET /api/v1/articles/archived}.</p>
 */
@Component
public class ArticleArchivingScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ArticleArchivingScheduler.class);

    /** Number of days after publication before an article is considered stale. */
    static final int ARCHIVE_AFTER_DAYS = 7;

    private final ArticleRepository articleRepository;

    public ArticleArchivingScheduler(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    /**
     * Runs at midnight (server timezone) every day.
     * Batch-updates PUBLISHED articles older than {@value #ARCHIVE_AFTER_DAYS} days to ARCHIVED.
     *
     * @return number of articles archived in this run (useful for tests/monitoring)
     */
    @Scheduled(cron = "0 0 0 * * *")
    public int archiveStaleArticles() {
        Instant cutoff = Instant.now().minus(ARCHIVE_AFTER_DAYS, ChronoUnit.DAYS);
        logger.info("[ArchivingScheduler] Archiving PUBLISHED articles with pubDate < {} ...", cutoff);

        int archived = articleRepository.bulkUpdateStatus(
                ArticleStatus.PUBLISHED, ArticleStatus.ARCHIVED, cutoff);

        logger.info("[ArchivingScheduler] Archived {} article(s).", archived);
        return archived;
    }
}

