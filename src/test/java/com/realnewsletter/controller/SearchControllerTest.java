package com.realnewsletter.controller;

import com.realnewsletter.model.ArticleStatus;
import com.realnewsletter.model.NewsdataArticle;
import com.realnewsletter.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link SearchController}.
 *
 * <p>Uses H2 in-memory database via the {@code test} Spring profile.
 * Verifies keyword search, category filter, dateRange filter, pagination,
 * and that non-PUBLISHED articles are excluded.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SearchControllerTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private ArticleRepository articleRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        articleRepository.deleteAll();
    }

    // ── Basic search ─────────────────────────────────────────────────────────────

    @Test
    void search_shouldReturnMatchingArticlesByTitle() throws Exception {
        articleRepository.save(new NewsdataArticle("http://re1.com", "Real Estate Market Boom", "content about housing"));
        articleRepository.save(new NewsdataArticle("http://tech1.com", "Tech Giants Report Earnings", "quarterly results"));

        mockMvc.perform(get("/api/v1/search")
                        .param("query", "real estate")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].link").value("http://re1.com"));
    }

    @Test
    void search_shouldReturnMatchingArticlesByContent() throws Exception {
        articleRepository.save(new NewsdataArticle("http://art1.com", "Latest News", "housing market is booming this quarter"));
        articleRepository.save(new NewsdataArticle("http://art2.com", "Sports Update", "football championship results"));

        mockMvc.perform(get("/api/v1/search")
                        .param("query", "housing market")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].link").value("http://art1.com"));
    }

    @Test
    void search_shouldReturnEmptyPageWhenNoMatch() throws Exception {
        articleRepository.save(new NewsdataArticle("http://art1.com", "Some Article Title", "some content here"));

        mockMvc.perform(get("/api/v1/search")
                        .param("query", "quantum physics")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void search_shouldBeCaseInsensitive() throws Exception {
        articleRepository.save(new NewsdataArticle("http://art1.com", "STOCK MARKET RALLY", "wall street gains"));

        mockMvc.perform(get("/api/v1/search")
                        .param("query", "stock market")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    // ── Category filter ──────────────────────────────────────────────────────────

    @Test
    void search_shouldFilterByCategory() throws Exception {
        NewsdataArticle sports = new NewsdataArticle("http://sp.com", "Football Championship Results", "football championship");
        sports.setCategory("sports");
        NewsdataArticle tech   = new NewsdataArticle("http://tc.com", "Football Analytics Platform", "football data analysis");
        tech.setCategory("technology");
        articleRepository.save(sports);
        articleRepository.save(tech);

        mockMvc.perform(get("/api/v1/search")
                        .param("query", "football")
                        .param("category", "sports")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].link").value("http://sp.com"));
    }

    @Test
    void search_shouldIgnoreBlankCategory() throws Exception {
        articleRepository.save(new NewsdataArticle("http://art1.com", "Finance News today", "market update"));
        articleRepository.save(new NewsdataArticle("http://art2.com", "Finance Summary weekly", "weekly finance recap"));

        mockMvc.perform(get("/api/v1/search")
                        .param("query", "finance")
                        .param("category", "   ")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    // ── Date range filter ────────────────────────────────────────────────────────

    @Test
    void search_shouldFilterByDateRange_last7days() throws Exception {
        // Use pubDate (user-settable) for date range filtering, as @CreationTimestamp
        // is set by Hibernate and cannot be overridden in tests.
        NewsdataArticle recent = new NewsdataArticle("http://recent.com", "Recent AI News", "ai update");
        recent.setPubDate(Instant.now().minus(3, ChronoUnit.DAYS));

        NewsdataArticle old = new NewsdataArticle("http://old.com", "Old AI Article", "old ai content");
        old.setPubDate(Instant.now().minus(20, ChronoUnit.DAYS));

        articleRepository.save(recent);
        articleRepository.save(old);

        mockMvc.perform(get("/api/v1/search")
                        .param("query", "ai")
                        .param("dateRange", "last7days")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].link").value("http://recent.com"));
    }

    @Test
    void search_shouldFilterByDateRange_last30days() throws Exception {
        NewsdataArticle withinRange = new NewsdataArticle("http://w1.com", "Climate Change Report", "global warming news");
        withinRange.setPubDate(Instant.now().minus(15, ChronoUnit.DAYS));

        NewsdataArticle outsideRange = new NewsdataArticle("http://w2.com", "Climate Science Data", "old climate data");
        outsideRange.setPubDate(Instant.now().minus(60, ChronoUnit.DAYS));

        articleRepository.save(withinRange);
        articleRepository.save(outsideRange);

        mockMvc.perform(get("/api/v1/search")
                        .param("query", "climate")
                        .param("dateRange", "last30days")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].link").value("http://w1.com"));
    }

    @Test
    void search_shouldNotFilterWhenDateRangeIsUnknown() throws Exception {
        articleRepository.save(new NewsdataArticle("http://a1.com", "Economy Article Update", "economy news 1"));
        articleRepository.save(new NewsdataArticle("http://a2.com", "Economy Forecast Annual", "economy news 2"));

        mockMvc.perform(get("/api/v1/search")
                        .param("query", "economy")
                        .param("dateRange", "invalidRange")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void search_shouldFilterByDateRange_last90days() throws Exception {
        NewsdataArticle withinRange = new NewsdataArticle("http://r90.com", "Technology Trends Review", "tech review quarterly");
        withinRange.setPubDate(Instant.now().minus(45, ChronoUnit.DAYS));

        NewsdataArticle outsideRange = new NewsdataArticle("http://o90.com", "Technology History Overview", "old tech history");
        outsideRange.setPubDate(Instant.now().minus(120, ChronoUnit.DAYS));

        articleRepository.save(withinRange);
        articleRepository.save(outsideRange);

        mockMvc.perform(get("/api/v1/search")
                        .param("query", "technology")
                        .param("dateRange", "last90days")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].link").value("http://r90.com"));
    }

    // ── Validation ───────────────────────────────────────────────────────────────

    @Test
    void search_shouldReturn400WhenQueryIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/search").accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isBadRequest());
    }

    // ── Status filtering ─────────────────────────────────────────────────────────

    @Test
    void search_shouldOnlyReturnPublishedArticles() throws Exception {
        NewsdataArticle published = new NewsdataArticle("http://pub.com", "Published Health Article", "health news");
        published.setStatus(ArticleStatus.PUBLISHED);

        NewsdataArticle disabled = new NewsdataArticle("http://dis.com", "Disabled Health Article", "health content");
        disabled.setStatus(ArticleStatus.DISABLED);

        NewsdataArticle archived = new NewsdataArticle("http://arc.com", "Archived Health Article", "old health info");
        archived.setStatus(ArticleStatus.ARCHIVED);

        articleRepository.save(published);
        articleRepository.save(disabled);
        articleRepository.save(archived);

        mockMvc.perform(get("/api/v1/search")
                        .param("query", "health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].link").value("http://pub.com"));
    }

    // ── Pagination ───────────────────────────────────────────────────────────────

    @Test
    void search_shouldRespectPaginationParameters() throws Exception {
        for (int i = 1; i <= 5; i++) {
            articleRepository.save(new NewsdataArticle(
                    "http://pg" + i + ".com", "Politics Article " + i, "political content " + i));
        }

        mockMvc.perform(get("/api/v1/search")
                        .param("query", "politics")
                        .param("page", "0")
                        .param("size", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(3))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.number").value(0));
    }

    @Test
    void search_shouldReturnSecondPage() throws Exception {
        for (int i = 1; i <= 4; i++) {
            articleRepository.save(new NewsdataArticle(
                    "http://sp" + i + ".com", "Space Exploration News " + i, "rocket launch details " + i));
        }

        mockMvc.perform(get("/api/v1/search")
                        .param("query", "space")
                        .param("page", "1")
                        .param("size", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(4));
    }

    // ── Response structure ───────────────────────────────────────────────────────

    @Test
    void search_responseShouldContainExpectedFields() throws Exception {
        articleRepository.save(new NewsdataArticle("http://field.com", "Innovation in Robotics sector", "robot arm development"));

        mockMvc.perform(get("/api/v1/search")
                        .param("query", "robotics")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].title").value("Innovation in Robotics sector"))
                .andExpect(jsonPath("$.content[0].link").value("http://field.com"))
                .andExpect(jsonPath("$.content[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }
}

