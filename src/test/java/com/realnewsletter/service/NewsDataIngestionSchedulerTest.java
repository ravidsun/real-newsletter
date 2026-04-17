package com.realnewsletter.service;

import com.realnewsletter.config.NewsDataSchedulerProperties;
import com.realnewsletter.model.NewsdataArticle;
import com.realnewsletter.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NewsDataIngestionSchedulerTest {

    @Mock private NewsDataSchedulerProperties props;
    @Mock private ExternalNewsClient newsClient;
    @Mock private ArticleRepository articleRepository;

    @InjectMocks
    private NewsDataIngestionScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(props.isEnabled()).thenReturn(true);
        when(props.getMaxRequests()).thenReturn(3);
        when(props.getPageSize()).thenReturn(10);
        when(props.getCountry()).thenReturn("us");
        when(props.getLanguage()).thenReturn("en");
        when(props.getCategory()).thenReturn("general");
    }

    @Test
    void shouldSkipRunWhenDisabled() {
        when(props.isEnabled()).thenReturn(false);
        scheduler.runIngestion();
        verifyNoInteractions(newsClient, articleRepository);
    }

    @Test
    void shouldSaveNewArticlesAndSkipDuplicates() {
        // First page has 2 articles, no next page
        ExternalNewsClient.NewsdataArticleRaw raw1 = makeRaw("id1", "http://a.com/1");
        ExternalNewsClient.NewsdataArticleRaw raw2 = makeRaw("id2", "http://a.com/2");

        ExternalNewsClient.NewsdataResponse page1 =
                new ExternalNewsClient.NewsdataResponse("ok", List.of(raw1, raw2), null);

        when(newsClient.fetchPage(any(), any(), any(), isNull(), anyInt()))
                .thenReturn(Mono.just(page1));

        NewsdataArticle article1 = new NewsdataArticle("http://a.com/1", "Title1", "body1");
        NewsdataArticle article2 = new NewsdataArticle("http://a.com/2", "Title2", "body2");
        when(newsClient.mapToArticle(raw1)).thenReturn(article1);
        when(newsClient.mapToArticle(raw2)).thenReturn(article2);

        when(articleRepository.existsByLink("http://a.com/1")).thenReturn(false);
        when(articleRepository.existsByLink("http://a.com/2")).thenReturn(true); // duplicate

        scheduler.runIngestion();

        verify(articleRepository, times(1)).save(article1);
        verify(articleRepository, never()).save(article2);
    }

    @Test
    void shouldFollowNextPageCursorUntilExhausted() {
        ExternalNewsClient.NewsdataArticleRaw raw = makeRaw("id1", "http://a.com/1");

        ExternalNewsClient.NewsdataResponse page1 =
                new ExternalNewsClient.NewsdataResponse("ok", List.of(raw), "page2token");
        ExternalNewsClient.NewsdataResponse page2 =
                new ExternalNewsClient.NewsdataResponse("ok", List.of(), null);

        when(newsClient.fetchPage(any(), any(), any(), isNull(), anyInt()))
                .thenReturn(Mono.just(page1));
        when(newsClient.fetchPage(any(), any(), any(), eq("page2token"), anyInt()))
                .thenReturn(Mono.just(page2));

        NewsdataArticle article = new NewsdataArticle("http://a.com/1", "Title1", "body1");
        when(newsClient.mapToArticle(raw)).thenReturn(article);
        when(articleRepository.existsByLink("http://a.com/1")).thenReturn(false);

        scheduler.runIngestion();

        // fetchPage called twice: page1 (null cursor) + page2 (page2token cursor, empty → stop)
        verify(newsClient, times(2)).fetchPage(any(), any(), any(), any(), anyInt());
        verify(articleRepository, times(1)).save(article);
    }

    @Test
    void shouldStopOnApiError() {
        when(newsClient.fetchPage(any(), any(), any(), isNull(), anyInt()))
                .thenReturn(Mono.error(new RuntimeException("API down")));

        scheduler.runIngestion();

        verify(articleRepository, never()).save(any());
    }

    private ExternalNewsClient.NewsdataArticleRaw makeRaw(String id, String link) {
        return new ExternalNewsClient.NewsdataArticleRaw(
                id, "Title", link, "desc", "content",
                null, null, "en", null, null,
                null, null, null, null, null,
                "src", "Source", 1, "http://src.com", null, null, null);
    }
}

