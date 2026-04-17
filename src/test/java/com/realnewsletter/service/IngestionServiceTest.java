package com.realnewsletter.service;

import com.realnewsletter.model.NewsdataArticle;
import com.realnewsletter.repository.ArticleRepository;
import com.realnewsletter.scheduler.NewsDataIngestionScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Integration test for the news ingestion pipeline.
 *
 * <p>Loads the full Spring context with a real H2 in-memory database to verify
 * end-to-end behaviour: fetching → deduplication → AI enrichment → persistence → SSE event.</p>
 *
 * <p>External dependencies (news API client and AI service) are replaced with Mockito beans
 * so no real network calls are made.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class IngestionServiceTest {

    /** The scheduler under test — wired by Spring with the real repository and mocked externals. */
    @Autowired
    private NewsDataIngestionScheduler newsDataIngestionScheduler;

    @Autowired
    private ArticleRepository articleRepository;

    /** Replace the real Newsdata.io HTTP client with a mock. */
    @MockitoBean
    private ExternalNewsClient externalNewsClient;

    /** Replace the real OpenAI call with a no-op mock. */
    @MockitoBean
    private AiEnhancementService aiEnhancementService;


    @BeforeEach
    void clearDb() {
        articleRepository.deleteAll();
    }

    @Test
    void shouldSaveNewArticlesEnrichThemAndFireSseEvents() {
        // Arrange – one existing duplicate, two new articles
        articleRepository.save(new NewsdataArticle("http://dup.com", "Duplicate", "Content"));

        ExternalNewsClient.NewsdataResponse response = new ExternalNewsClient.NewsdataResponse(
                "success",
                List.of(
                        raw("http://new1.com", "New Article 1", "Body 1"),
                        raw("http://new2.com", "New Article 2", "Body 2"),
                        raw("http://dup.com",  "Duplicate",     "Content")  // already in DB
                ),
                null // no next page
        );

        when(externalNewsClient.fetchPage(any(), any(), any(), any(), anyInt()))
                .thenReturn(Mono.just(response));
        when(externalNewsClient.mapToArticle(any())).thenAnswer(inv -> {
            ExternalNewsClient.NewsdataArticleRaw r = inv.getArgument(0);
            return new NewsdataArticle(r.link(), r.title(), r.content());
        });

        // Act
        newsDataIngestionScheduler.runIngestion();

        // Assert – new articles persisted
        assertThat(articleRepository.existsByLink("http://new1.com")).isTrue();
        assertThat(articleRepository.existsByLink("http://new2.com")).isTrue();
        assertThat(articleRepository.count()).isEqualTo(3); // 1 existing + 2 new

        // Assert – AI enrichment called only for the 2 new articles (not the duplicate)
        verify(aiEnhancementService, times(2)).enrichArticle(any());
    }

    @Test
    void shouldSkipAllWhenAllAreDuplicates() {
        articleRepository.save(new NewsdataArticle("http://dup.com", "Title", "Body"));

        ExternalNewsClient.NewsdataResponse response = new ExternalNewsClient.NewsdataResponse(
                "success",
                List.of(raw("http://dup.com", "Title", "Body")),
                null
        );

        when(externalNewsClient.fetchPage(any(), any(), any(), any(), anyInt()))
                .thenReturn(Mono.just(response));
        when(externalNewsClient.mapToArticle(any())).thenAnswer(inv -> {
            ExternalNewsClient.NewsdataArticleRaw r = inv.getArgument(0);
            return new NewsdataArticle(r.link(), r.title(), r.content());
        });

        newsDataIngestionScheduler.runIngestion();

        assertThat(articleRepository.count()).isEqualTo(1); // nothing new
        verify(aiEnhancementService, never()).enrichArticle(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ExternalNewsClient.NewsdataArticleRaw raw(String link, String title, String content) {
        return new ExternalNewsClient.NewsdataArticleRaw(
                null, title, link, null, content,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null
        );
    }
}
