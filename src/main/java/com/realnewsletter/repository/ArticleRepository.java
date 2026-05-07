package com.realnewsletter.repository;

import com.realnewsletter.model.Article;
import com.realnewsletter.model.ArticleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository for Article entity.
 */
@Repository
public interface ArticleRepository extends JpaRepository<Article, UUID>,
                                           JpaSpecificationExecutor<Article> {

    boolean existsByLink(String link);

    boolean existsByTitleHash(String titleHash);

    /**
     * Bulk-archives all {@link ArticleStatus#PUBLISHED} articles whose {@code pubDate}
     * is earlier than {@code cutoff}.  Returns the number of rows updated.
     *
     * <p>Called by the daily archiving scheduler to move stale content off the public feed.</p>
     */
    @Modifying
    @Transactional
    @Query("UPDATE Article a SET a.status = :newStatus " +
           "WHERE a.status = :currentStatus AND a.pubDate < :cutoff")
    int bulkUpdateStatus(@Param("currentStatus") ArticleStatus currentStatus,
                         @Param("newStatus")     ArticleStatus newStatus,
                         @Param("cutoff")        Instant        cutoff);

    /**
     * Full-text keyword search over PUBLISHED articles using case-insensitive LIKE on
     * {@code title} and {@code content} fields. H2-compatible (no tsvector required).
     *
     * <p>Optionally narrows results by {@code category} and a {@code publishedAfter} cutoff
     * (for dateRange filtering based on {@code pubDate}). Pass {@code null} for unset filters.</p>
     *
     * @param query          keyword to search (matched with {@code %query%} pattern)
     * @param category       optional category token to match (exact substring in title or content)
     * @param publishedAfter optional lower bound on {@code pubDate}; pass {@code null} to skip
     * @param pageable       pagination and sort configuration
     * @return page of matching PUBLISHED articles
     */
    @Query("SELECT a FROM Article a WHERE a.status = com.realnewsletter.model.ArticleStatus.PUBLISHED " +
           "AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "     OR LOWER(a.content) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND (:category IS NULL OR LOWER(TREAT(a AS com.realnewsletter.model.NewsdataArticle).category) LIKE LOWER(CONCAT('%', :category, '%'))) " +
           "AND (:publishedAfter IS NULL OR a.pubDate >= :publishedAfter)")
    Page<Article> searchPublished(@Param("query")          String  query,
                                  @Param("category")       String  category,
                                  @Param("publishedAfter") Instant publishedAfter,
                                  Pageable pageable);
}
