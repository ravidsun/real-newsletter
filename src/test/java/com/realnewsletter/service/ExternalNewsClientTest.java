package com.realnewsletter.service;

import com.realnewsletter.dto.ArticleDto;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;

@SpringBootTest
@TestPropertySource(properties = {
    "external.news.api.url=http://localhost:8080/top-headlines",
    "external.news.api.key=testkey"
})
class ExternalNewsClientTest {

    @Autowired
    private ExternalNewsClient externalNewsClient;

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start(8080);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void fetchTrendingArticles_shouldReturnArticles() {
        String mockResponse = """
            {
                "articles": [
                    {"url": "http://example.com/1", "title": "Title 1", "content": "Content 1"},
                    {"url": "http://example.com/2", "title": "Title 2", "content": "Content 2"}
                ]
            }
            """;
        mockWebServer.enqueue(new MockResponse().setBody(mockResponse).setResponseCode(200));

        StepVerifier.create(externalNewsClient.fetchTrendingArticles())
            .expectNextMatches(dto -> dto.url().equals("http://example.com/1") && dto.title().equals("Title 1"))
            .expectNextMatches(dto -> dto.url().equals("http://example.com/2") && dto.title().equals("Title 2"))
            .verifyComplete();
    }

    @Test
    void fetchTrendingArticles_shouldHandleError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(externalNewsClient.fetchTrendingArticles())
            .expectError(RuntimeException.class)
            .verify();
    }
}
