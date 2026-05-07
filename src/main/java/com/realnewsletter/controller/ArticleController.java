package com.realnewsletter.controller;

import com.realnewsletter.dto.ArticleDto;
import com.realnewsletter.dto.ArticleStatusUpdateRequest;
import com.realnewsletter.model.Article;
import com.realnewsletter.repository.ArticleRepository;
import com.realnewsletter.repository.ArticleSpecification;
import com.realnewsletter.service.ArticleStreamService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * REST controller exposing paginated article listing, admin management, and SSE streaming endpoints.
 */
@RestController
@RequestMapping("/api/v1/articles")
public class ArticleController {

    private final ArticleRepository articleRepository;
    private final ArticleStreamService articleStreamService;

    public ArticleController(ArticleRepository articleRepository,
                             ArticleStreamService articleStreamService) {
        this.articleRepository = articleRepository;
        this.articleStreamService = articleStreamService;
    }

    /**
     * Returns a paginated list of articles sorted by {@code createdAt} DESC by default.
     * Only PUBLISHED articles are returned — DISABLED and ARCHIVED are excluded.
     */
    @GetMapping
    public Page<ArticleDto> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String category) {

        return articleRepository
                .findAll(ArticleSpecification.withFilters(country, language, category), pageable)
                .map(ArticleDto::fromEntity);
    }

    /**
     * Returns a paginated list of ARCHIVED articles (articles older than 7 days).
     * Supports the same optional filters as the main feed.
     */
    @GetMapping("/archived")
    public Page<ArticleDto> listArchived(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String category) {

        return articleRepository
                .findAll(ArticleSpecification.archivedWithFilters(country, language, category), pageable)
                .map(ArticleDto::fromEntity);
    }

    /**
     * Updates the lifecycle status of an article (admin operation).
     * Allows enabling/disabling articles or changing to any {@link com.realnewsletter.model.ArticleStatus}.
     *
     * @param id      UUID of the article
     * @param request body containing the new {@code status}
     * @return 200 with updated article DTO, or 404 if not found
     */
    @PutMapping("/{id}")
    public ResponseEntity<ArticleDto> updateStatus(@PathVariable UUID id,
                                                   @Valid @RequestBody ArticleStatusUpdateRequest request) {
        return articleRepository.findById(id)
                .map(article -> {
                    article.setStatus(request.status());
                    Article saved = articleRepository.save(article);
                    return ResponseEntity.ok(ArticleDto.fromEntity(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Permanently deletes an article (admin operation).
     *
     * @param id UUID of the article
     * @return 204 No Content on success, 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteArticle(@PathVariable UUID id) {
        if (!articleRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        articleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Opens a Server-Sent Events connection that will receive a {@code new-article}
     * event for every article ingested after the client connects.
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return articleStreamService.subscribe();
    }
}
