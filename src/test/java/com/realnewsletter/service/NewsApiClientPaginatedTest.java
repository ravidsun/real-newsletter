package com.realnewsletter.service;

import com.realnewsletter.model.NewsApiArticle;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for {@link NewsApiClient} paginated fetch and mapToArticle helper.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "external.newsapi.url=http://localhost:8091/v2/top-headlines",
    "external.newsapi.key=testkey"
})
class NewsApiClientPaginatedTest {

    @Autowired
    private NewsApiClient newsApiClient;

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start(8091);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void fetchPage_shouldReturnArticlesAndTotalResults() {
        String mockResponse = """
            {
                "status": "ok",
                "totalResults": 2,
                "articles": [
                    {
                        "source": { "id": "bbc-news", "name": "BBC News" },
                        "author": "Reporter One",
                        "title": "Breaking News",
                        "description": "Something happened",
                        "url": "https://bbc.com/news/1",
                        "urlToImage": "https://img.bbc.com/1.jpg",
                        "publishedAt": "2026-04-17T07:00:00Z",
                        "content": "Full article content here"
                    }
                ]
            }
            """;
        mockWebServer.enqueue(new MockResponse()
            .setBody(mockResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        NewsApiClient.NewsApiResponse response =
            newsApiClient.fetchPage("us", "en", "general", 1, 20).block();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("ok");
        assertThat(response.totalResults()).isEqualTo(2);
        assertThat(response.articles()).hasSize(1);
        assertThat(response.articles().get(0).title()).isEqualTo("Breaking News");
        assertThat(response.articles().get(0).source().name()).isEqualTo("BBC News");
    }

    @Test
    void fetchPage_shouldHandleApiError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        assertThrows(Exception.class,
            () -> newsApiClient.fetchPage("us", "en", "general", 1, 20).block());
    }

    @Test
    void mapToArticle_shouldMapAllFieldsCorrectly() {
        NewsApiClient.NewsApiArticleRaw raw = new NewsApiClient.NewsApiArticleRaw(
            new NewsApiClient.NewsApiSource("cnn", "CNN"),
            "Jane Doe",
            "Big Story",
            "A big story happened",
            "https://cnn.com/story/1",
            "https://img.cnn.com/story.jpg",
            "2026-04-17T09:00:00Z",
            "Full story content"
        );

        NewsApiArticle article = newsApiClient.mapToArticle(raw);

        assertThat(article.getLink()).isEqualTo("https://cnn.com/story/1");
        assertThat(article.getTitle()).isEqualTo("Big Story");
        assertThat(article.getContent()).isEqualTo("Full story content");
        assertThat(article.getDescription()).isEqualTo("A big story happened");
        assertThat(article.getCreator()).isEqualTo("Jane Doe");
        assertThat(article.getImageUrl()).isEqualTo("https://img.cnn.com/story.jpg");
        assertThat(article.getSourceId()).isEqualTo("cnn");
        assertThat(article.getSourceName()).isEqualTo("CNN");
        assertThat(article.getPubDate()).isNotNull();
    }

    @Test
    void mapToArticle_shouldHandleNullSource() {
        NewsApiClient.NewsApiArticleRaw raw = new NewsApiClient.NewsApiArticleRaw(
            null, "Author", "Title", "Desc",
            "https://example.com/1", null,
            "2026-04-17T09:00:00Z", "Content"
        );

        NewsApiArticle article = newsApiClient.mapToArticle(raw);

        assertThat(article.getSourceId()).isNull();
        assertThat(article.getSourceName()).isNull();
    }

    @Test
    void mapToArticle_shouldHandleInvalidPublishedAt() {
        NewsApiClient.NewsApiArticleRaw raw = new NewsApiClient.NewsApiArticleRaw(
            null, "Author", "Title", "Desc",
            "https://example.com/1", null,
            "not-a-date", "Content"
        );

        // Should not throw; pubDate will simply be null
        NewsApiArticle article = newsApiClient.mapToArticle(raw);
        assertThat(article.getPubDate()).isNull();
    }
}

