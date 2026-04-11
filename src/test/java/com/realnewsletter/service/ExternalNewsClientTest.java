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
    "external.news.api.url=http://localhost:8089/api/1/news",
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
    void fetchTrendingArticles_shouldReturnArticlesFromNewsdata() {
        // Newsdata.io response shape: { status, results[].{title, link, description, content, pubDate} }
        String mockResponse = """
            {
                "status": "success",
                "totalResults": 2,
                "results": [
                    {
                        "title": "Title 1",
                        "link": "http://example.com/1",
                        "description": "Short description 1",
                        "content": "Full content 1",
                        "pubDate": "2026-04-11 09:00:00"
                    },
                    {
                        "title": "Title 2",
                        "link": "http://example.com/2",
                        "description": "Short description 2",
                        "content": null,
                        "pubDate": "2026-04-11 09:05:00"
                    }
                ]
            }
            """;
        mockWebServer.enqueue(new MockResponse()
            .setBody(mockResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        StepVerifier.create(externalNewsClient.fetchTrendingArticles())
            // First article: content present → used as body
            .expectNextMatches(dto ->
                    dto.link().equals("http://example.com/1") &&
                    dto.title().equals("Title 1") &&
                    dto.content().equals("Full content 1"))
            // Second article: content null (free-tier restriction) → falls back to description
            .expectNextMatches(dto ->
                    dto.link().equals("http://example.com/2") &&
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
