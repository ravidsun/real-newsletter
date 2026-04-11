package com.realnewsletter.service;

import com.realnewsletter.dto.ArticleDto;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

@SpringBootTest
class IngestionServiceTest {

    @Autowired
    private IngestionService ingestionService;

    @MockBean
    private ExternalNewsClient externalNewsClient;

    @Test
    void ingestScheduled_shouldLogFetchedArticles() {
        List<ArticleDto> mockArticles = List.of(
            new ArticleDto(UUID.randomUUID(), "url1", "title1", "content1"),
            new ArticleDto(UUID.randomUUID(), "url2", "title2", "content2")
        );
        Mockito.when(externalNewsClient.fetchTrendingArticles()).thenReturn(Flux.fromIterable(mockArticles));

        ingestionService.ingestScheduled();

        Mockito.verify(externalNewsClient).fetchTrendingArticles();
    }
}
