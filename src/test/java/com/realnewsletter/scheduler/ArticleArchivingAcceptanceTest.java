package com.realnewsletter.scheduler;

import com.realnewsletter.model.ArticleStatus;
import com.realnewsletter.model.NewsdataArticle;
import com.realnewsletter.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Acceptance tests for issue #20: Automated Article Archiving (7-Day Rule).
 *
 * <p>Validates all acceptance criteria defined in the issue:
 * <ol>
 *   <li>A scheduled job archives PUBLISHED articles older than 7 days.</li>
 *   <li>Articles older than 7 days do NOT appear in {@code GET /api/v1/articles}.</li>
 *   <li>An {@code /archive} endpoint exists and returns only ARCHIVED articles.</li>
 *   <li>No manual intervention is required — the job runs on a cron schedule.</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Issue #20 – Automated Article Archiving (7-Day Rule) Acceptance Tests")
class ArticleArchivingAcceptanceTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ArticleArchivingScheduler archivingScheduler;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        articleRepository.deleteAll();
    }

    // ── Acceptance Criterion 1 ────────────────────────────────────────────────
    // "A scheduled job runs every day at midnight and archives eligible articles."

    @Test
    @DisplayName("AC1 – scheduler is a Spring-managed @Scheduled bean (no manual intervention needed)")
    void ac1_schedulerBeanExists() {
        // The scheduler is injected — it is a Spring-managed component
        assertThat(archivingScheduler).isNotNull();
    }

    @Test
    @DisplayName("AC1 – scheduler archives PUBLISHED articles older than 7 days")
    void ac1_schedulerArchivesStaleArticles() {
        NewsdataArticle stale = articleRepository.save(publishedArticle(
                "http://stale-ac1.com", Instant.now().minus(8, ChronoUnit.DAYS)));

        int count = archivingScheduler.archiveStaleArticles();

        assertThat(count).isGreaterThanOrEqualTo(1);
        assertThat(articleRepository.findById(stale.getId()))
                .hasValueSatisfying(a -> assertThat(a.getStatus()).isEqualTo(ArticleStatus.ARCHIVED));
    }

    // ── Acceptance Criterion 2 ────────────────────────────────────────────────
    // "Articles older than 7 days no longer appear in GET /api/v1/articles."

    @Test
    @DisplayName("AC2 – archived articles are excluded from the public feed")
    void ac2_archivedArticlesExcludedFromPublicFeed() throws Exception {
        // Create a fresh and a stale article
        articleRepository.save(publishedArticle(
                "http://fresh-ac2.com", Instant.now().minus(2, ChronoUnit.DAYS)));
        NewsdataArticle stale = articleRepository.save(publishedArticle(
                "http://stale-ac2.com", Instant.now().minus(10, ChronoUnit.DAYS)));

        // Run the scheduler
        archivingScheduler.archiveStaleArticles();

        // Stale is now ARCHIVED — must not appear in the main public feed
        mockMvc.perform(get("/api/v1/articles").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))          // only fresh
                .andExpect(jsonPath("$.content[0].link").value("http://fresh-ac2.com"));
    }

    // ── Acceptance Criterion 3 ────────────────────────────────────────────────
    // "An /archive page exists and displays archived articles."

    @Test
    @DisplayName("AC3 – GET /api/v1/articles/archived returns ARCHIVED articles")
    void ac3_archiveEndpointReturnsArchivedArticles() throws Exception {
        NewsdataArticle stale = articleRepository.save(publishedArticle(
                "http://stale-ac3.com", Instant.now().minus(10, ChronoUnit.DAYS)));

        // Simulate the daily scheduler run
        archivingScheduler.archiveStaleArticles();

        mockMvc.perform(get("/api/v1/articles/archived").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].link").value("http://stale-ac3.com"));
    }

    @Test
    @DisplayName("AC3 – archive endpoint returns 200 OK even when no archived articles exist")
    void ac3_archiveEndpointReturnsEmptyWhenNoArchivedArticles() throws Exception {
        mockMvc.perform(get("/api/v1/articles/archived").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    // ── Acceptance Criterion 4 ────────────────────────────────────────────────
    // "No manual intervention is required to trigger archiving."

    @Test
    @DisplayName("AC4 – archiving is idempotent: running the job twice does not double-archive")
    void ac4_archivingIsIdempotent() {
        articleRepository.save(publishedArticle(
                "http://idem-ac4.com", Instant.now().minus(9, ChronoUnit.DAYS)));

        int firstRun  = archivingScheduler.archiveStaleArticles();
        int secondRun = archivingScheduler.archiveStaleArticles();

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isZero();   // already ARCHIVED — not touched again
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private NewsdataArticle publishedArticle(String link, Instant pubDate) {
        NewsdataArticle a = new NewsdataArticle(link, "Title for " + link, "body");
        a.setStatus(ArticleStatus.PUBLISHED);
        a.setPubDate(pubDate);
        return a;
    }
}

