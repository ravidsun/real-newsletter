package com.realnewsletter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.realnewsletter.model.ArticleStatus;
import com.realnewsletter.model.NewsdataArticle;
import com.realnewsletter.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the admin article lifecycle management endpoints
 * (PUT /api/v1/articles/{id} and DELETE /api/v1/articles/{id}).
 * Implements issue #24 acceptance criteria.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminArticleControllerTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private ArticleRepository articleRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(springSecurity())
                .build();
        articleRepository.deleteAll();
    }

    // ── PUT /{id} — Status update ──────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void putArticle_shouldDisableArticle() throws Exception {
        NewsdataArticle article = articleRepository.save(
                new NewsdataArticle("http://a.com", "Test Article", "body"));

        String body = objectMapper.writeValueAsString(Map.of("status", "DISABLED"));

        mockMvc.perform(put("/api/v1/articles/" + article.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DISABLED")));

        assertThat(articleRepository.findById(article.getId()))
                .hasValueSatisfying(a -> assertThat(a.getStatus()).isEqualTo(ArticleStatus.DISABLED));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void putArticle_shouldEnableDisabledArticle() throws Exception {
        NewsdataArticle article = new NewsdataArticle("http://b.com", "Disabled Article", "body");
        article.setStatus(ArticleStatus.DISABLED);
        article = articleRepository.save(article);

        String body = objectMapper.writeValueAsString(Map.of("status", "PUBLISHED"));

        mockMvc.perform(put("/api/v1/articles/" + article.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PUBLISHED")));

        assertThat(articleRepository.findById(article.getId()))
                .hasValueSatisfying(a -> assertThat(a.getStatus()).isEqualTo(ArticleStatus.PUBLISHED));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void putArticle_shouldReturn404ForNonExistentArticle() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("status", "DISABLED"));

        mockMvc.perform(put("/api/v1/articles/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void putArticle_shouldReturn400ForInvalidStatus() throws Exception {
        NewsdataArticle article = articleRepository.save(
                new NewsdataArticle("http://c.com", "Valid Article", "body"));

        String body = "{\"status\": \"INVALID_STATUS\"}";

        mockMvc.perform(put("/api/v1/articles/" + article.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /{id} ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteArticle_shouldReturn204AndRemoveFromDb() throws Exception {
        NewsdataArticle article = articleRepository.save(
                new NewsdataArticle("http://d.com", "To Delete", "body"));

        mockMvc.perform(delete("/api/v1/articles/" + article.getId()))
                .andExpect(status().isNoContent());

        assertThat(articleRepository.existsById(article.getId())).isFalse();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteArticle_shouldReturn404ForNonExistentArticle() throws Exception {
        mockMvc.perform(delete("/api/v1/articles/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ── Public feed hides DISABLED articles ───────────────────────────────────

    @Test
    void listArticles_shouldNotReturnDisabledArticles() throws Exception {
        NewsdataArticle published = articleRepository.save(
                new NewsdataArticle("http://pub.com", "Published", "body"));

        NewsdataArticle disabled = new NewsdataArticle("http://dis.com", "Disabled", "body");
        disabled.setStatus(ArticleStatus.DISABLED);
        articleRepository.save(disabled);

        mockMvc.perform(get("/api/v1/articles").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].link", is("http://pub.com")));
    }

    @Test
    void listArticles_shouldReturnPublishedArticleWithCorrectStatus() throws Exception {
        articleRepository.save(new NewsdataArticle("http://pub2.com", "Published2", "body"));

        mockMvc.perform(get("/api/v1/articles").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status", is("PUBLISHED")));
    }
}

