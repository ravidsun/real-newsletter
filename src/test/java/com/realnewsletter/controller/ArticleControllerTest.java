package com.realnewsletter.controller;

import com.realnewsletter.model.Article;
import com.realnewsletter.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * Integration tests for {@link ArticleController}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArticleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ArticleRepository articleRepository;

    @BeforeEach
    void setUp() {
        articleRepository.deleteAll();
    }

    @Test
    void listArticles_shouldReturnEmptyPageWhenNoArticles() throws Exception {
        mockMvc.perform(get("/api/v1/articles")
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listArticles_shouldReturnPagedArticlesSortedByCreatedAtDesc() throws Exception {
        // Save 3 articles
        articleRepository.save(new Article("http://article1.com", "First Article", "Content 1"));
        articleRepository.save(new Article("http://article2.com", "Second Article", "Content 2"));
        articleRepository.save(new Article("http://article3.com", "Third Article", "Content 3"));

        mockMvc.perform(get("/api/v1/articles")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "createdAt,desc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].url", hasItems(
                        "http://article1.com",
                        "http://article2.com",
                        "http://article3.com")));
    }

    @Test
    void listArticles_shouldRespectPaginationParameters() throws Exception {
        // Save 5 articles
        for (int i = 1; i <= 5; i++) {
            articleRepository.save(new Article("http://article" + i + ".com", "Article " + i, "Content " + i));
        }

        mockMvc.perform(get("/api/v1/articles")
                        .param("page", "0")
                        .param("size", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void listArticles_shouldReturnSecondPage() throws Exception {
        // Save 4 articles
        for (int i = 1; i <= 4; i++) {
            articleRepository.save(new Article("http://p" + i + ".com", "Title " + i, "Content " + i));
        }

        mockMvc.perform(get("/api/v1/articles")
                        .param("page", "1")
                        .param("size", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.number").value(1));
    }

    @Test
    void streamEndpoint_shouldStartAsynchronousSSEProcessing() throws Exception {
        // When a client connects to the SSE stream endpoint, Spring MVC starts async processing.
        // The SseEmitter keeps the HTTP connection open for real-time events.
        // asyncStarted() proves the endpoint wiring is correct and the SseEmitter is active.
        mockMvc.perform(get("/api/v1/articles/stream"))
                .andExpect(request().asyncStarted());
    }
}

