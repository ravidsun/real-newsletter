package com.realnewsletter.controller;

import com.realnewsletter.dto.ArticleDto;
import com.realnewsletter.service.SearchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing semantic/keyword article search.
 *
 * <p>Endpoint: {@code GET /api/v1/search}</p>
 *
 * <p>Query parameters:
 * <ul>
 *   <li>{@code query}     – required search keyword (matched against title and content)</li>
 *   <li>{@code category}  – optional category filter</li>
 *   <li>{@code dateRange} – optional date range: {@code last7days}, {@code last30days},
 *                           {@code last90days}</li>
 *   <li>Standard Spring pagination params: {@code page}, {@code size}, {@code sort}</li>
 * </ul>
 * </p>
 *
 * <p>Only {@link com.realnewsletter.model.ArticleStatus#PUBLISHED} articles are returned.
 * Results include standard Spring pagination metadata: {@code page.totalElements},
 * {@code page.number}, {@code page.size}, {@code page.totalPages}.</p>
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Searches articles by keyword with optional category and date-range filters.
     *
     * @param query     keyword to search (required)
     * @param category  optional category filter; omit or leave blank for no filter
     * @param dateRange optional date range token ({@code last7days}, {@code last30days},
     *                  {@code last90days}); omit for no date filter
     * @param pageable  pagination configuration (default: 20 per page, sorted by createdAt DESC)
     * @return 200 with a paginated list of {@link ArticleDto} results;
     *         400 if {@code query} is missing or blank
     */
    @GetMapping
    public ResponseEntity<Page<ArticleDto>> search(
            @RequestParam String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String dateRange,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Page<ArticleDto> results = searchService.search(query, category, dateRange, pageable);
        return ResponseEntity.ok(results);
    }
}

