package com.realnewsletter.repository;

import com.realnewsletter.model.Article;
import com.realnewsletter.model.ArticleStatus;
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
}
