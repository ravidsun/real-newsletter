package com.realnewsletter.repository;

import com.realnewsletter.model.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for Article entity.
 */
@Repository
public interface ArticleRepository extends JpaRepository<Article, UUID> {

    boolean existsByLink(String link);
}
