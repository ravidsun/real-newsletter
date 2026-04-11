package com.realnewsletter.controller;

import com.realnewsletter.dto.ArticleDto;
import com.realnewsletter.repository.ArticleRepository;
import com.realnewsletter.service.ArticleStreamService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
     * Supports standard Spring Data Pageable query parameters:
     * {@code ?page=0&size=10&sort=createdAt,desc}
     */
    @GetMapping
    public Page<ArticleDto> list(
            @PageableDefault(size = 2, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return articleRepository.findAll(pageable).map(ArticleDto::fromEntity);
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

