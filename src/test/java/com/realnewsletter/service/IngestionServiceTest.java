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

        // Mock ArticleDtos with all required fields (minimal setup)
        ArticleDto dto1 = new ArticleDto(null, "id1", "http://new1.com", "New Title 1", null, "New Content 1", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, false, null);
        ArticleDto dto2 = new ArticleDto(null, "id2", "http://new2.com", "New Title 2", null, "New Content 2", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, false, null);
        ArticleDto dupe = new ArticleDto(null, "dup", "http://duplicate.com", "Duplicate Title", null, "Duplicate Content", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, false, null);

        when(externalNewsClient.fetchTrendingArticles()).thenReturn(Flux.fromIterable(List.of(dto1, dto2, dupe)));

        ingestionService.ingestScheduled();

        // Verify new articles are saved
        assertThat(articleRepository.existsByLink("http://new1.com")).isTrue();
        assertThat(articleRepository.existsByLink("http://new2.com")).isTrue();
        // Duplicate should not be saved again
        assertThat(articleRepository.findAll()).hasSize(3); // 1 existing + 2 new
    }
}
