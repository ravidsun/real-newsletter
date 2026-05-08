package com.realnewsletter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.realnewsletter.model.NewsdataArticle;
import com.realnewsletter.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests verifying the security hardening requirements from issue #30:
 *
 * <ul>
 *   <li>RBAC – POST, PUT, DELETE on articles require the {@code ADMIN} role (HTTP 403 otherwise).</li>
 *   <li>HTML sanitization – XSS payloads in article bodies are stripped before persistence.</li>
 *   <li>CORS – preflight from an unlisted origin is rejected.</li>
 *   <li>Rate limiting – exceeding the Bucket4j threshold on search returns HTTP 429.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class SecurityIntegrationTest {

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

    // ══════════════════════════════════════════════════════════════════════════
    // RBAC – 403 for non-admin / anonymous users
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @WithAnonymousUser
    @DisplayName("POST /api/v1/articles without ADMIN role → 403")
    void createArticle_anonymousUser_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("link", "http://anon.com", "title", "Title", "content", "body"));

        mockMvc.perform(post("/api/v1/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("POST /api/v1/articles with USER role (non-admin) → 403")
    void createArticle_userRole_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("link", "http://user.com", "title", "Title", "content", "body"));

        mockMvc.perform(post("/api/v1/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("PUT /api/v1/articles/{id} without ADMIN role → 403")
    void updateArticle_anonymousUser_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("status", "DISABLED"));

        mockMvc.perform(put("/api/v1/articles/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("PUT /api/v1/articles/{id} with USER role (non-admin) → 403")
    void updateArticle_userRole_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("status", "DISABLED"));

        mockMvc.perform(put("/api/v1/articles/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("DELETE /api/v1/articles/{id} without ADMIN role → 403")
    void deleteArticle_anonymousUser_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/articles/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("DELETE /api/v1/articles/{id} with USER role (non-admin) → 403")
    void deleteArticle_userRole_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/articles/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST/PUT/DELETE /api/v1/articles with ADMIN role → 2xx")
    void adminUser_canPerformStateChangingOperations() throws Exception {
        // Create
        String createBody = objectMapper.writeValueAsString(
                Map.of("link", "http://admin-create.com", "title", "Admin Article", "content", "body"));

        String response = mockMvc.perform(post("/api/v1/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(response).get("id").asText();

        // Update status
        String updateBody = objectMapper.writeValueAsString(Map.of("status", "DISABLED"));
        mockMvc.perform(put("/api/v1/articles/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        // Delete
        mockMvc.perform(delete("/api/v1/articles/" + id))
                .andExpect(status().isNoContent());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HTML Sanitization – XSS payloads stripped before persistence
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("XSS payload in article title is sanitized before persistence")
    void createArticle_xssInTitle_isSanitized() throws Exception {
        String xssTitle = "<script>alert('xss')</script>Safe Title";
        String body = objectMapper.writeValueAsString(
                Map.of("link", "http://xss-title.com", "title", xssTitle, "content", "safe content"));

        mockMvc.perform(post("/api/v1/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                // <script> tag must be stripped from the response body
                .andExpect(jsonPath("$.title").value("Safe Title"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("XSS payload in article content is sanitized before persistence")
    void createArticle_xssInContent_isSanitized() throws Exception {
        String xssContent = "<img src=x onerror=\"alert(1)\">Some content";
        String body = objectMapper.writeValueAsString(
                Map.of("link", "http://xss-content.com", "title", "Clean Title", "content", xssContent));

        mockMvc.perform(post("/api/v1/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                // onerror attribute must be removed
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.not(containsString("onerror"))));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CORS – preflight rejects unlisted origins
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CORS preflight from allowed origin (localhost:4201) is accepted")
    void cors_allowedOrigin_preflightAccepted() throws Exception {
        mockMvc.perform(options("/api/v1/articles")
                        .header("Origin", "http://localhost:4201")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CORS preflight from unlisted origin is rejected (no ACAO header)")
    void cors_unlistedOrigin_preflightRejected() throws Exception {
        mockMvc.perform(options("/api/v1/articles")
                        .header("Origin", "http://evil-site.com")
                        .header("Access-Control-Request-Method", "GET"))
                // Spring Security CORS filter returns 403 for unlisted origins on preflight
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Rate limiting – Bucket4j on /api/v1/search returns HTTP 429
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Verifies that the Bucket4j rate limiter returns HTTP 429 when the bucket is
     * exhausted. This test enables the limiter with a capacity of 1 token via a
     * nested test-specific configuration class, then sends two consecutive requests
     * to the search endpoint. The second request must be rejected with 429.
     */
    @SpringBootTest
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
            "bucket4j-rate-limit.enabled=true",
            "bucket4j-rate-limit.capacity=1",
            "bucket4j-rate-limit.refill-tokens=1",
            "bucket4j-rate-limit.refill-period-seconds=60"
    })
    static class RateLimitTest {

        @Autowired
        private WebApplicationContext wac;

        @Autowired
        private ArticleRepository articleRepository;

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                    .apply(springSecurity())
                    .build();
            articleRepository.deleteAll();
            articleRepository.save(new NewsdataArticle(
                    "http://rate-limit-test.com", "Rate Limit Test Article", "content"));
        }

        @Test
        @DisplayName("Exceeding rate limit on /api/v1/search returns HTTP 429")
        void search_exceedingRateLimit_returns429() throws Exception {
            // First request – should succeed (1 token available)
            mockMvc.perform(get("/api/v1/search")
                            .param("query", "rate")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            // Second request – bucket empty, should be rate-limited
            mockMvc.perform(get("/api/v1/search")
                            .param("query", "rate")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("X-RateLimit-Remaining"))
                    .andExpect(header().exists("Retry-After"));
        }
    }
}

