package com.realnewsletter.service;

import com.realnewsletter.scheduler.NewsApiIngestionScheduler;
import com.realnewsletter.config.NewsApiSchedulerProperties;
import com.realnewsletter.model.NewsApiArticle;
import com.realnewsletter.repository.ArticleRepository;
import com.realnewsletter.service.AiEnhancementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NewsApiIngestionSchedulerTest {

    @Mock private NewsApiSchedulerProperties props;
    @Mock private NewsApiClient newsApiClient;
    @Mock private ArticleRepository articleRepository;
    @Mock private AiEnhancementService aiEnhancementService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NewsApiIngestionScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(props.isEnabled()).thenReturn(true);
        when(props.getMaxRequests()).thenReturn(3);
        when(props.getPageSize()).thenReturn(20);
        when(props.getCountry()).thenReturn("us");
        when(props.getLanguage()).thenReturn("en");
        when(props.getCategory()).thenReturn("general");
    }

    @Test
    void shouldSkipRunWhenDisabled() {
        when(props.isEnabled()).thenReturn(false);
        scheduler.runIngestion();
        verifyNoInteractions(newsApiClient, articleRepository);
    }

    @Test
    void shouldSaveNewArticlesAndSkipDuplicates() {
        NewsApiClient.NewsApiArticleRaw raw1 = makeRaw("http://a.com/1", "Title 1");
        NewsApiClient.NewsApiArticleRaw raw2 = makeRaw("http://a.com/2", "Title 2");

        // Page 1 returns 2 articles (< pageSize=20 → last page)
        NewsApiClient.NewsApiResponse page1 =
                new NewsApiClient.NewsApiResponse("ok", 2, List.of(raw1, raw2));

        when(newsApiClient.fetchPage(any(), any(), any(), eq(1), anyInt()))
                .thenReturn(Mono.just(page1));

        NewsApiArticle article1 = new NewsApiArticle("http://a.com/1", "Title 1", "body");
        NewsApiArticle article2 = new NewsApiArticle("http://a.com/2", "Title 2", "body");
        when(newsApiClient.mapToArticle(raw1)).thenReturn(article1);
        when(newsApiClient.mapToArticle(raw2)).thenReturn(article2);

        when(articleRepository.existsByLink("http://a.com/1")).thenReturn(false);
        when(articleRepository.existsByLink("http://a.com/2")).thenReturn(true); // duplicate

        scheduler.runIngestion();

        verify(articleRepository, times(1)).save(article1);
        verify(articleRepository, never()).save(article2);
    }

    @Test
    void shouldPageThroughMultiplePagesUntilPartialPage() {
        NewsApiClient.NewsApiArticleRaw raw = makeRaw("http://a.com/1", "T");

        // Page 1: full page (20 articles) — return 20 articles, totalResults=25
        List<NewsApiClient.NewsApiArticleRaw> fullPage = java.util.Collections.nCopies(20, raw);
        NewsApiClient.NewsApiResponse page1 =
                new NewsApiClient.NewsApiResponse("ok", 25, fullPage);

        // Page 2: partial page (5 articles) → stop
        List<NewsApiClient.NewsApiArticleRaw> partialPage = java.util.Collections.nCopies(5, raw);
        NewsApiClient.NewsApiResponse page2 =
                new NewsApiClient.NewsApiResponse("ok", 25, partialPage);

        when(newsApiClient.fetchPage(any(), any(), any(), eq(1), anyInt()))
                .thenReturn(Mono.just(page1));
        when(newsApiClient.fetchPage(any(), any(), any(), eq(2), anyInt()))
                .thenReturn(Mono.just(page2));

        NewsApiArticle article = new NewsApiArticle("http://a.com/1", "T", "b");
        when(newsApiClient.mapToArticle(raw)).thenReturn(article);
        when(articleRepository.existsByLink(any())).thenReturn(false);

        scheduler.runIngestion();

        verify(newsApiClient, times(2)).fetchPage(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void shouldStopWhenTotalResultsExhausted() {
        NewsApiClient.NewsApiArticleRaw raw = makeRaw("http://a.com/1", "T");

        // Page 1: full page (20), totalResults=20 → all fetched → stop
        List<NewsApiClient.NewsApiArticleRaw> fullPage = java.util.Collections.nCopies(20, raw);
        NewsApiClient.NewsApiResponse page1 =
                new NewsApiClient.NewsApiResponse("ok", 20, fullPage);

        when(newsApiClient.fetchPage(any(), any(), any(), eq(1), anyInt()))
                .thenReturn(Mono.just(page1));

        NewsApiArticle article = new NewsApiArticle("http://a.com/1", "T", "b");
        when(newsApiClient.mapToArticle(raw)).thenReturn(article);
        when(articleRepository.existsByLink(any())).thenReturn(false);

        scheduler.runIngestion();

        // Only 1 page fetched even though maxRequests=3
        verify(newsApiClient, times(1)).fetchPage(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void shouldStopOnApiError() {
        when(newsApiClient.fetchPage(any(), any(), any(), eq(1), anyInt()))
                .thenReturn(Mono.error(new RuntimeException("API down")));

        scheduler.runIngestion();

        verify(articleRepository, never()).save(any());
    }

    @Test
    void shouldStopAfterMaxRequests() {
        NewsApiClient.NewsApiArticleRaw raw = makeRaw("http://a.com/1", "T");

        // Every page is full (pageSize=20) and totalResults is huge → would page forever without cap
        List<NewsApiClient.NewsApiArticleRaw> fullPage = java.util.Collections.nCopies(20, raw);
        NewsApiClient.NewsApiResponse pageWithMore =
                new NewsApiClient.NewsApiResponse("ok", 10_000, fullPage);

        when(newsApiClient.fetchPage(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Mono.just(pageWithMore));

        NewsApiArticle article = new NewsApiArticle("http://a.com/1", "T", "b");
        when(newsApiClient.mapToArticle(raw)).thenReturn(article);
        when(articleRepository.existsByLink(any())).thenReturn(false);

        scheduler.runIngestion();

        // maxRequests=3 (set in @BeforeEach) — must stop after exactly 3 pages
        verify(newsApiClient, times(3)).fetchPage(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void shouldSkipArticlesWithNullLink() {
        NewsApiClient.NewsApiArticleRaw rawNullUrl = makeRaw(null, "Title null url");

        NewsApiClient.NewsApiResponse page1 =
                new NewsApiClient.NewsApiResponse("ok", 1, List.of(rawNullUrl));

        when(newsApiClient.fetchPage(any(), any(), any(), eq(1), anyInt()))
                .thenReturn(Mono.just(page1));

        NewsApiArticle article = new NewsApiArticle(null, "Title null url", "body");
        when(newsApiClient.mapToArticle(rawNullUrl)).thenReturn(article);

        scheduler.runIngestion();

        verify(articleRepository, never()).save(any());
    }

    private NewsApiClient.NewsApiArticleRaw makeRaw(String url, String title) {
        return new NewsApiClient.NewsApiArticleRaw(
                new NewsApiClient.NewsApiSource("src-id", "Source Name"),
                "Author", title, "description", url,
                "http://img.com/pic.jpg", "2026-04-17T07:00:00Z", "content body");
    }
}

