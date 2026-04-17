package com.realnewsletter.service;

import com.realnewsletter.model.NewsdataArticle;
import com.realnewsletter.model.NewsApiArticle;
import com.realnewsletter.repository.ArticleRepository;
import com.realnewsletter.scheduler.IngestionScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class IngestionServiceTest {

    @Autowired
    private IngestionScheduler ingestionService;

    @Autowired
    private ArticleRepository articleRepository;

    @MockitoBean
    private ExternalNewsClient externalNewsClient;

    @MockitoBean
    private NewsApiClient newsApiClient;

    @MockitoBean
    private AiEnhancementService aiEnhancementService;

    @Test
    void ingestScheduled_shouldSaveNewArticlesAndSkipDuplicates() {
        // Pre-save one article to test deduplication
        NewsdataArticle existingArticle = new NewsdataArticle("http://duplicate.com", "Duplicate Title", "Duplicate Content");
        articleRepository.save(existingArticle);

        NewsdataArticle dto1 = new NewsdataArticle("http://new1.com", "New Title 1", "New Content 1");
        NewsdataArticle dto2 = new NewsdataArticle("http://new2.com", "New Title 2", "New Content 2");
        NewsdataArticle dupe = new NewsdataArticle("http://duplicate.com", "Duplicate Title", "Duplicate Content");

        when(externalNewsClient.fetchTrendingArticles()).thenReturn(Flux.just(dto1, dto2, dupe));
        when(newsApiClient.fetchTopHeadlines()).thenReturn(Flux.empty());

        ingestionService.ingestScheduled();

        assertThat(articleRepository.existsByLink("http://new1.com")).isTrue();
        assertThat(articleRepository.existsByLink("http://new2.com")).isTrue();
        assertThat(articleRepository.findAll()).hasSize(3); // 1 existing + 2 new
    }
}
