package com.realnewsletter.service;

import com.realnewsletter.dto.ArticleDto;
import com.realnewsletter.model.Article;
import com.realnewsletter.repository.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class IngestionServiceTest {

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private ArticleRepository articleRepository;

    @MockitoBean
    private ExternalNewsClient externalNewsClient;

    @MockitoBean
    private AiEnhancementService aiEnhancementService;

    @Test
    void ingestScheduled_shouldSaveNewArticlesAndSkipDuplicates() {
        // Pre-save one article to test deduplication
        Article existingArticle = new Article("http://duplicate.com", "Duplicate Title", "Duplicate Content");
        articleRepository.save(existingArticle);

        List<ArticleDto> mockArticles = List.of(
            new ArticleDto("http://new1.com", "New Title 1", "New Content 1"),
            new ArticleDto("http://new2.com", "New Title 2", "New Content 2"),
            new ArticleDto("http://duplicate.com", "Duplicate Title", "Duplicate Content") // duplicate
        );
        when(externalNewsClient.fetchTrendingArticles()).thenReturn(Flux.fromIterable(mockArticles));

        ingestionService.ingestScheduled();

        // Verify new articles are saved
        assertThat(articleRepository.existsByUrl("http://new1.com")).isTrue();
        assertThat(articleRepository.existsByUrl("http://new2.com")).isTrue();
        // Duplicate should not be saved again
        assertThat(articleRepository.findAll()).hasSize(3); // 1 existing + 2 new
    }
}
