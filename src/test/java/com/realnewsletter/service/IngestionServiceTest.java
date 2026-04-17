package com.realnewsletter.service;

import com.realnewsletter.config.NewsDataSchedulerProperties;
import com.realnewsletter.model.NewsdataArticle;
import com.realnewsletter.repository.ArticleRepository;
import com.realnewsletter.scheduler.NewsDataIngestionScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test for the core ingestion logic (deduplication, save, AI enrichment)
 * via NewsDataIngestionScheduler.
 */
@SpringBootTest
@ActiveProfiles("test")
class IngestionServiceTest {

    @Autowired
    private ArticleRepository articleRepository;

    @Test
    void ingestion_shouldSaveNewArticlesAndSkipDuplicates() {
        // Arrange
        ExternalNewsClient mockClient = mock(ExternalNewsClient.class);
        AiEnhancementService mockAi = mock(AiEnhancementService.class);
        ApplicationEventPublisher mockPublisher = mock(ApplicationEventPublisher.class);
        NewsDataSchedulerProperties props = new NewsDataSchedulerProperties();
        props.setEnabled(true);
        props.setMaxRequests(1);
        props.setPageSize(10);

        NewsdataArticle existing = new NewsdataArticle("http://duplicate.com", "Duplicate", "Content");
        articleRepository.save(existing);

        NewsdataArticle article1 = new NewsdataArticle("http://new1.com", "New 1", "Content 1");
        NewsdataArticle article2 = new NewsdataArticle("http://new2.com", "New 2", "Content 2");
        NewsdataArticle dupe     = new NewsdataArticle("http://duplicate.com", "Duplicate", "Content");

        ExternalNewsClient.NewsdataResponse response = new ExternalNewsClient.NewsdataResponse(
                "success",
                List.of(
                        toRaw(article1),
                        toRaw(article2),
                        toRaw(dupe)
                ),
                null
        );

        when(mockClient.fetchPage(any(), any(), any(), any(), anyInt())).thenReturn(Mono.just(response));
        when(mockClient.mapToArticle(any())).thenAnswer(inv -> {
            ExternalNewsClient.NewsdataArticleRaw raw = inv.getArgument(0);
            return new NewsdataArticle(raw.link(), raw.title(), raw.content());
        });

        NewsDataIngestionScheduler scheduler = new NewsDataIngestionScheduler(
                props, mockClient, articleRepository, mockAi, mockPublisher);

        // Act
        scheduler.runIngestion();

        // Assert
        assertThat(articleRepository.existsByLink("http://new1.com")).isTrue();
        assertThat(articleRepository.existsByLink("http://new2.com")).isTrue();
        verify(mockAi, times(2)).enrichArticle(any()); // only 2 new articles enriched
        verify(mockPublisher, times(2)).publishEvent(any(Object.class)); // 2 SSE events fired
    }

    private ExternalNewsClient.NewsdataArticleRaw toRaw(NewsdataArticle a) {
        return new ExternalNewsClient.NewsdataArticleRaw(
                null,           // article_id
                a.getTitle(),   // title
                a.getLink(),    // link
                null,           // description
                a.getContent(), // content
                null,           // keywords
                null,           // creator
                null,           // language
                null,           // country
                null,           // category
                null,           // image_url
                null,           // video_url
                null,           // pubDate
                null,           // pubDateTZ
                null,           // fetched_at
                null,           // source_id
                null,           // source_name
                null,           // source_priority
                null,           // source_url
                null,           // source_icon
                null,           // sentiment
                null            // sentiment_stats
        );
    }
}
