package com.realnewsletter.client;

import com.realnewsletter.dto.ArticleDto;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ExternalNewsClient}.
 *
 * <p>A {@link MockWebServer} stands in for the real external API so that
 * tests are fast, deterministic, and offline-capable.</p>
 */
class ExternalNewsClientTest {

    private MockWebServer mockWebServer;
    private ExternalNewsClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/v2/top-headlines").toString();

        WebClient webClient = WebClient.builder().build();
        client = new ExternalNewsClient(webClient, baseUrl, "test-api-key");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void fetchTrendingArticles_returnsArticlesFromApi() {
        // language=JSON
        String responseBody = """
                {
                  "status": "ok",
                  "totalResults": 2,
                  "articles": [
                    {
                      "url": "https://example.com/article1",
                      "title": "First Article",
                      "content": "Content of first article"
                    },
                    {
                      "url": "https://example.com/article2",
                      "title": "Second Article",
                      "content": "Content of second article"
                    }
                  ]
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(responseBody)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        Flux<ArticleDto> result = client.fetchTrendingArticles();

        StepVerifier.create(result)
                .assertNext(article -> {
                    assertThat(article.getUrl()).isEqualTo("https://example.com/article1");
                    assertThat(article.getTitle()).isEqualTo("First Article");
                    assertThat(article.getContent()).isEqualTo("Content of first article");
                })
                .assertNext(article -> {
                    assertThat(article.getUrl()).isEqualTo("https://example.com/article2");
                    assertThat(article.getTitle()).isEqualTo("Second Article");
                    assertThat(article.getContent()).isEqualTo("Content of second article");
                })
                .verifyComplete();
    }

    @Test
    void fetchTrendingArticles_returnsEmptyFlux_whenNoArticles() {
        // language=JSON
        String responseBody = """
                {
                  "status": "ok",
                  "totalResults": 0,
                  "articles": []
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(responseBody)
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        Flux<ArticleDto> result = client.fetchTrendingArticles();

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void fetchTrendingArticles_throwsException_onErrorResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"message\": \"Unauthorized\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        Flux<ArticleDto> result = client.fetchTrendingArticles();

        StepVerifier.create(result)
                .expectErrorMatches(ex ->
                        ex instanceof ExternalNewsClient.ExternalNewsApiException
                                && ex.getMessage().contains("401"))
                .verify();
    }

    @Test
    void fetchTrendingArticles_throwsException_onServerError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"message\": \"Internal Server Error\"}")
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        Flux<ArticleDto> result = client.fetchTrendingArticles();

        StepVerifier.create(result)
                .expectErrorMatches(ex ->
                        ex instanceof ExternalNewsClient.ExternalNewsApiException
                                && ex.getMessage().contains("500"))
                .verify();
    }
}

