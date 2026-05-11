package com.realnewsletter.service;

import com.realnewsletter.dto.ArticleDto;
import com.realnewsletter.repository.ArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Service responsible for semantic/keyword-based article search.
 *
 * <p>Since a vector database is not available, search is implemented using
 * case-insensitive LIKE queries on {@code title} and {@code content} fields,
 * which are H2-compatible for tests and translate to {@code ILIKE} on PostgreSQL
 * via JPQL's {@code LOWER()} + {@code LIKE} pattern.</p>
 *
 * <p>Only {@link com.realnewsletter.model.ArticleStatus#PUBLISHED} articles are returned.</p>
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final ArticleRepository articleRepository;

    public SearchService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    /**
     * Searches PUBLISHED articles by keyword, with optional category and date-range filters.
     *
     * <p>The {@code dateRange} parameter supports the following values:
     * <ul>
     *   <li>{@code last7days}  – articles with {@code pubDate} in the last 7 days</li>
     *   <li>{@code last30days} – articles with {@code pubDate} in the last 30 days</li>
     *   <li>{@code last90days} – articles with {@code pubDate} in the last 90 days</li>
     *   <li>{@code null} or blank – no date filter applied</li>
     * </ul>
     * </p>
     *
     * @param query     keyword to search (required, matched against title and content)
     * @param category  optional category filter; {@code null} or blank = no filter
     * @param dateRange optional date range token; {@code null} or blank = no filter
     * @param pageable  pagination and sort configuration
     * @return paginated {@link ArticleDto} results
     */
    public Page<ArticleDto> search(String query, String category, String dateRange, Pageable pageable) {
        log.debug("Searching articles: query='{}', category='{}', dateRange='{}'", query, category, dateRange);

        Instant publishedAfter = resolveDateRange(dateRange);
        String categoryFilter = (category != null && !category.isBlank()) ? category.trim() : null;

        return articleRepository
                .searchPublished(query, categoryFilter, publishedAfter, pageable)
                .map(ArticleDto::fromEntity);
    }

    /**
     * Resolves a human-readable date-range token to an {@link Instant} lower bound
     * applied against the article's {@code pubDate} field.
     *
     * @param dateRange token string (e.g. {@code "last7days"}); may be null
     * @return the cutoff {@link Instant}, or {@code null} when no filter should be applied
     */
    private Instant resolveDateRange(String dateRange) {
        if (dateRange == null || dateRange.isBlank()) {
            return null;
        }
        Instant now = Instant.now();
        return switch (dateRange.trim().toLowerCase()) {
            case "last7days"  -> now.minus(7,  ChronoUnit.DAYS);
            case "last30days" -> now.minus(30, ChronoUnit.DAYS);
            case "last90days" -> now.minus(90, ChronoUnit.DAYS);
            default -> {
                log.warn("Unknown dateRange value '{}'; ignoring date filter", dateRange);
                yield null;
            }
        };
    }
}

