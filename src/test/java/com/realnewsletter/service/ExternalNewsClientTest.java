package com.realnewsletter.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import reactor.test.StepVerifier;

import java.io.IOException;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "external.news.api.url=http://localhost:8089/v2/articles",
    "external.news.api.key=testkey"
})
class ExternalNewsClientTest {

    @Autowired
    private ExternalNewsClient externalNewsClient;

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start(8089);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void fetchTrendingArticles_shouldReturnArticlesFromFinlight() {
        // Finlight response shape: articles[].{title, link, description, content, publishedAt}
        String mockResponse = """
            {
                "articles": [
                    {
                        "title": "Title 1",
                        "link": "http://example.com/1",
                        "description": "Short description 1",
                        "content": "Full content 1",
                        "publishedAt": "2026-04-11T09:00:00Z"
                    },
                    {
                        "title": "Title 2",
                        "link": "http://example.com/2",
                        "description": "Short description 2",
                        "content": null,
                        "publishedAt": "2026-04-11T09:05:00Z"
                    }
                ]
            }
            """;
        mockWebServer.enqueue(new MockResponse()
            .setBody(mockResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        StepVerifier.create(externalNewsClient.fetchTrendingArticles())
            // First article: content is present → used as article content
            .expectNextMatches(dto ->
                    dto.url().equals("http://example.com/1") &&
                    dto.title().equals("Title 1") &&
                    dto.content().equals("Full content 1"))
            // Second article: content is null → falls back to description
            .expectNextMatches(dto ->
                    dto.url().equals("http://example.com/2") &&
                    dto.title().equals("Title 2") &&
                    dto.content().equals("Short description 2"))
            .verifyComplete();
    }

    @Test
    void fetchTrendingArticles_shouldHandleApiError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(externalNewsClient.fetchTrendingArticles())
            .expectError(RuntimeException.class)
            .verify();
    }
}
