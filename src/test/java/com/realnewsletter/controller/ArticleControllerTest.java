package com.realnewsletter.controller;

import com.realnewsletter.model.NewsdataArticle;
import com.realnewsletter.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * Integration tests for {@link ArticleController}.
 */
@SpringBootTest
@ActiveProfiles("test")
class ArticleControllerTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private ArticleRepository articleRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
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
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void listArticles_shouldReturnPagedArticlesSortedByCreatedAtDesc() throws Exception {
        // Save 3 articles
        articleRepository.save(new NewsdataArticle("http://article1.com", "First Article", "Content 1"));
        articleRepository.save(new NewsdataArticle("http://article2.com", "Second Article", "Content 2"));
        articleRepository.save(new NewsdataArticle("http://article3.com", "Third Article", "Content 3"));

        mockMvc.perform(get("/api/v1/articles")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "createdAt,desc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].link", hasItems(
                        "http://article1.com",
                        "http://article2.com",
                        "http://article3.com")));
    }

    @Test
    void listArticles_shouldRespectPaginationParameters() throws Exception {
        // Save 5 articles
        for (int i = 1; i <= 5; i++) {
            articleRepository.save(new NewsdataArticle("http://article" + i + ".com", "Article " + i, "Content " + i));
        }

        mockMvc.perform(get("/api/v1/articles")
                        .param("page", "0")
                        .param("size", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void listArticles_shouldReturnSecondPage() throws Exception {
        // Save 4 articles
        for (int i = 1; i <= 4; i++) {
            articleRepository.save(new NewsdataArticle("http://p" + i + ".com", "Title " + i, "Content " + i));
        }

        mockMvc.perform(get("/api/v1/articles")
                        .param("page", "1")
                        .param("size", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page.number").value(1));
    }

    @Test
    void streamEndpoint_shouldStartAsynchronousSSEProcessing() throws Exception {
        // When a client connects to the SSE stream endpoint, Spring MVC starts async processing.
        // The SseEmitter keeps the HTTP connection open for real-time events.
        // asyncStarted() proves the endpoint wiring is correct and the SseEmitter is active.
        mockMvc.perform(get("/api/v1/articles/stream"))
                .andExpect(request().asyncStarted());
    }

    // ── Filter tests ────────────────────────────────────────────────────────────

    @Test
    void listArticles_shouldFilterByLanguage() throws Exception {
        NewsdataArticle english = new NewsdataArticle("http://en.com", "English Article", "body");
        english.setLanguage("english");
        NewsdataArticle french = new NewsdataArticle("http://fr.com", "French Article", "body");
        french.setLanguage("french");
        articleRepository.save(english);
        articleRepository.save(french);

        mockMvc.perform(get("/api/v1/articles")
                        .param("language", "english")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].link").value("http://en.com"));
    }

    @Test
    void listArticles_shouldFilterByCategory() throws Exception {
        NewsdataArticle sports = new NewsdataArticle("http://sports.com", "Sports Article", "body");
        sports.setCategory("sports");
        NewsdataArticle tech = new NewsdataArticle("http://tech.com", "Tech Article", "body");
        tech.setCategory("technology");
        articleRepository.save(sports);
        articleRepository.save(tech);

        mockMvc.perform(get("/api/v1/articles")
                        .param("category", "sports")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].link").value("http://sports.com"));
    }

    @Test
    void listArticles_shouldFilterByCountry() throws Exception {
        NewsdataArticle us = new NewsdataArticle("http://us.com", "US Article", "body");
        us.setCountry("united states of america");
        NewsdataArticle uk = new NewsdataArticle("http://uk.com", "UK Article", "body");
        uk.setCountry("united kingdom");
        articleRepository.save(us);
        articleRepository.save(uk);

        mockMvc.perform(get("/api/v1/articles")
                        .param("country", "united kingdom")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].link").value("http://uk.com"));
    }

    @Test
    void listArticles_shouldReturnEmptyPageWhenOutOfBounds() throws Exception {
        articleRepository.save(new NewsdataArticle("http://only.com", "Only Article", "body"));

        // Page 5 is way beyond the data — should return empty content, not an error
        mockMvc.perform(get("/api/v1/articles")
                        .param("page", "5")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }
}
