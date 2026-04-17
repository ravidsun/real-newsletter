package com.realnewsletter.service;

import com.realnewsletter.model.NewsdataArticle;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ExternalNewsClient} paginated fetch and mapToArticle helper.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "external.newsdata.url=http://localhost:8090/api/1/news",
    "external.newsdata.key=testkey"
})
class ExternalNewsClientPaginatedTest {

    @Autowired
    private ExternalNewsClient externalNewsClient;

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start(8090);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void fetchPage_shouldReturnResponseWithNextPageCursor() {
        String mockResponse = """
            {
                "status": "success",
                "results": [
                    {
                        "article_id": "abc123",
                        "title": "Test Article",
                        "link": "http://example.com/test",
                        "description": "A test article",
                        "content": "Full test content",
                        "language": "en",
                        "source_id": "test_source",
                        "source_name": "Test Source",
                        "source_priority": 1,
                        "source_url": "http://test-source.com"
                    }
                ],
                "nextPage": "token_page2"
            }
            """;
        mockWebServer.enqueue(new MockResponse()
            .setBody(mockResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        ExternalNewsClient.NewsdataResponse response =
            externalNewsClient.fetchPage("us", "en", "general", null, 10).block();

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("success");
        assertThat(response.results()).hasSize(1);
        assertThat(response.nextPage()).isEqualTo("token_page2");
        assertThat(response.results().get(0).article_id()).isEqualTo("abc123");
    }

    @Test
    void fetchPage_shouldHandleApiError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(422));

        org.junit.jupiter.api.Assertions.assertThrows(
            Exception.class,
            () -> externalNewsClient.fetchPage("us", "en", "general", null, 10).block()
        );
    }

    @Test
    void mapToArticle_shouldMapAllFieldsCorrectly() {
        ExternalNewsClient.NewsdataArticleRaw raw = new ExternalNewsClient.NewsdataArticleRaw(
            "art001", "My Title", "http://example.com/article",
            "A description", "Full content",
            List.of("tech", "ai"), List.of("Author One"),
            "en", List.of("us"), List.of("technology"),
            "http://img.com/pic.jpg", null,
            "2026-04-17 06:00:00", "UTC", null,
            "src001", "Source Name", 5,
            "http://source.com", "http://source.com/icon.png",
            "positive", null
        );

        NewsdataArticle article = externalNewsClient.mapToArticle(raw);

        assertThat(article.getLink()).isEqualTo("http://example.com/article");
        assertThat(article.getTitle()).isEqualTo("My Title");
        assertThat(article.getContent()).isEqualTo("Full content");
        assertThat(article.getArticleId()).isEqualTo("art001");
        assertThat(article.getDescription()).isEqualTo("A description");
        assertThat(article.getKeywords()).isEqualTo("tech,ai");
        assertThat(article.getCreator()).isEqualTo("Author One");
        assertThat(article.getLanguage()).isEqualTo("en");
        assertThat(article.getCountry()).isEqualTo("us");
        assertThat(article.getCategory()).isEqualTo("technology");
        assertThat(article.getImageUrl()).isEqualTo("http://img.com/pic.jpg");
        assertThat(article.getVideoUrl()).isNull();
        assertThat(article.getSourceId()).isEqualTo("src001");
        assertThat(article.getSourceName()).isEqualTo("Source Name");
        assertThat(article.getSourcePriority()).isEqualTo(5);
        assertThat(article.getSourceUrl()).isEqualTo("http://source.com");
        assertThat(article.getSourceIcon()).isEqualTo("http://source.com/icon.png");
        assertThat(article.getSentiment()).isEqualTo("positive");
        assertThat(article.getSentimentStats()).isNull();
        assertThat(article.getPubDateTz()).isEqualTo("UTC");
        assertThat(article.getDuplicate()).isFalse();
    }

    @Test
    void mapToArticle_shouldFallBackToDescriptionWhenContentIsNull() {
        ExternalNewsClient.NewsdataArticleRaw raw = new ExternalNewsClient.NewsdataArticleRaw(
            "id2", "Title", "http://example.com/2",
            "Only description", null,
            null, null, "en", null, null,
            null, null, null, null, null,
            "s", "S", 1, "http://s.com", null, null, null
        );

        NewsdataArticle article = externalNewsClient.mapToArticle(raw);
        assertThat(article.getContent()).isEqualTo("Only description");
    }
}

