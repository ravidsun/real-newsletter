package com.realnewsletter.repository;

import com.realnewsletter.model.Article;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ArticleRepositoryTest {

    @Autowired
    private ArticleRepository articleRepository;

    @Test
    void existsByUrl_shouldReturnTrueWhenArticleExists() {
        Article article = new Article("http://example.com", "Title", "Content");
        articleRepository.save(article);

        boolean exists = articleRepository.existsByUrl("http://example.com");

        assertThat(exists).isTrue();
    }

    @Test
    void existsByUrl_shouldReturnFalseWhenArticleDoesNotExist() {
        boolean exists = articleRepository.existsByUrl("http://nonexistent.com");

        assertThat(exists).isFalse();
    }
}
