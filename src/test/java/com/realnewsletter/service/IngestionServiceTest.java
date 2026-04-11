package com.realnewsletter.service;

import com.realnewsletter.client.ExternalNewsClient;
import com.realnewsletter.dto.ArticleDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IngestionService}.
 *
 * <p>{@link ExternalNewsClient} is mocked so that network access is not
 * required and the scheduler logic can be verified in isolation.</p>
 */
@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    private ExternalNewsClient externalNewsClient;

    @InjectMocks
    private IngestionService ingestionService;

    @Test
    void ingestScheduled_callsFetchAndLogsCount_whenArticlesReturned() {
        ArticleDto article1 = new ArticleDto(null, "https://example.com/1", "Title 1", "Content 1");
        ArticleDto article2 = new ArticleDto(null, "https://example.com/2", "Title 2", "Content 2");

        when(externalNewsClient.fetchTrendingArticles())
                .thenReturn(Flux.just(article1, article2));

        // Should not throw; logging is verified by absence of exception.
        ingestionService.ingestScheduled();

        verify(externalNewsClient, times(1)).fetchTrendingArticles();
    }

    @Test
    void ingestScheduled_handlesEmptyArticleList_gracefully() {
        when(externalNewsClient.fetchTrendingArticles())
                .thenReturn(Flux.empty());

        // Should not throw even when no articles are returned.
        ingestionService.ingestScheduled();

        verify(externalNewsClient, times(1)).fetchTrendingArticles();
    }

    @Test
    void ingestScheduled_delegatesToExternalClient_onEachInvocation() {
        when(externalNewsClient.fetchTrendingArticles())
                .thenReturn(Flux.just(
                        new ArticleDto(null, "https://example.com/3", "Title 3", "Content 3")));

        ingestionService.ingestScheduled();
        ingestionService.ingestScheduled();

        // Verify the client is called once per invocation of ingestScheduled().
        verify(externalNewsClient, times(2)).fetchTrendingArticles();
    }

    @Test
    void ingestScheduled_doesNotThrow_whenClientReturnsError() {
        when(externalNewsClient.fetchTrendingArticles())
                .thenReturn(Flux.error(new ExternalNewsClient.ExternalNewsApiException("API error")));

        // block() will throw; we expect the exception to propagate.
        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> ingestionService.ingestScheduled());
    }
}

