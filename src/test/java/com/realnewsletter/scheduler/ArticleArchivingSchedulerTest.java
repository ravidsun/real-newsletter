package com.realnewsletter.scheduler;

import com.realnewsletter.model.ArticleStatus;
import com.realnewsletter.model.NewsdataArticle;
import com.realnewsletter.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ArticleArchivingScheduler}.
 * Verifies that the scheduler correctly archives old PUBLISHED articles
 * and leaves recent articles and non-PUBLISHED articles untouched.
 */
@SpringBootTest
@ActiveProfiles("test")
class ArticleArchivingSchedulerTest {

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleArchivingScheduler archivingScheduler;

    @BeforeEach
    void setUp() {
        articleRepository.deleteAll();
    }

    @Test
    void archiveStaleArticles_shouldArchivePublishedArticlesOlderThan7Days() {
        // Article published 10 days ago → should be archived
        NewsdataArticle stale = articleRepository.save(article(
                "http://stale.com", ArticleStatus.PUBLISHED,
                Instant.now().minus(10, ChronoUnit.DAYS)));

        // Article published 3 days ago → should stay PUBLISHED
        NewsdataArticle recent = articleRepository.save(article(
                "http://recent.com", ArticleStatus.PUBLISHED,
                Instant.now().minus(3, ChronoUnit.DAYS)));

        int archived = archivingScheduler.archiveStaleArticles();

        assertThat(archived).isEqualTo(1);
        assertThat(articleRepository.findById(stale.getId()))
                .hasValueSatisfying(a -> assertThat(a.getStatus()).isEqualTo(ArticleStatus.ARCHIVED));
        assertThat(articleRepository.findById(recent.getId()))
                .hasValueSatisfying(a -> assertThat(a.getStatus()).isEqualTo(ArticleStatus.PUBLISHED));
    }

    @Test
    void archiveStaleArticles_shouldNotArchiveDisabledArticles() {
        // DISABLED article that is old → must remain DISABLED, not become ARCHIVED
        NewsdataArticle disabled = articleRepository.save(article(
                "http://disabled.com", ArticleStatus.DISABLED,
                Instant.now().minus(10, ChronoUnit.DAYS)));

        int archived = archivingScheduler.archiveStaleArticles();

        assertThat(archived).isZero();
        assertThat(articleRepository.findById(disabled.getId()))
                .hasValueSatisfying(a -> assertThat(a.getStatus()).isEqualTo(ArticleStatus.DISABLED));
    }

    @Test
    void archiveStaleArticles_shouldNotArchiveAlreadyArchivedArticles() {
        NewsdataArticle alreadyArchived = articleRepository.save(article(
                "http://archived.com", ArticleStatus.ARCHIVED,
                Instant.now().minus(10, ChronoUnit.DAYS)));

        int archived = archivingScheduler.archiveStaleArticles();

        assertThat(archived).isZero();
        assertThat(articleRepository.findById(alreadyArchived.getId()))
                .hasValueSatisfying(a -> assertThat(a.getStatus()).isEqualTo(ArticleStatus.ARCHIVED));
    }

    @Test
    void archiveStaleArticles_shouldReturnZeroWhenNothingToArchive() {
        // Only fresh articles
        articleRepository.save(article("http://fresh.com", ArticleStatus.PUBLISHED,
                Instant.now().minus(1, ChronoUnit.DAYS)));

        int archived = archivingScheduler.archiveStaleArticles();

        assertThat(archived).isZero();
    }

    @Test
    void archiveStaleArticles_shouldHandleNullPubDate() {
        // Article with null pubDate should NOT be archived (query requires pubDate < cutoff)
        NewsdataArticle noPubDate = new NewsdataArticle("http://nopubdate.com", "No Date", "body");
        noPubDate.setStatus(ArticleStatus.PUBLISHED);
        // pubDate remains null
        articleRepository.save(noPubDate);

        int archived = archivingScheduler.archiveStaleArticles();

        assertThat(archived).isZero();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private NewsdataArticle article(String link, ArticleStatus status, Instant pubDate) {
        NewsdataArticle a = new NewsdataArticle(link, "Title " + link, "body");
        a.setStatus(status);
        a.setPubDate(pubDate);
        return a;
    }
}

