package com.realnewsletter.controller;

import com.realnewsletter.dto.ArticleDto;
import com.realnewsletter.repository.ArticleRepository;
import com.realnewsletter.repository.ArticleSpecification;
import com.realnewsletter.service.ArticleStreamService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST controller exposing paginated article listing and SSE streaming endpoints.
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
     *
     * <p>Optional filter parameters (all case-insensitive, AND-combined):
     * <ul>
     *   <li>{@code country}  – e.g. {@code ?country=in}
     *   <li>{@code language} – e.g. {@code ?language=en}
     *   <li>{@code category} – e.g. {@code ?category=business}
     * </ul>
     *
     * Supports standard Spring Data Pageable query parameters:
     * {@code ?page=0&size=10&sort=createdAt,desc}
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
     * Opens a Server-Sent Events connection that will receive a {@code new-article}
     * event for every article ingested after the client connects.
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return articleStreamService.subscribe();
    }
}
