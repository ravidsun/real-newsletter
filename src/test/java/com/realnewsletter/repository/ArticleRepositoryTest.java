package com.realnewsletter.repository;

import com.realnewsletter.model.Article;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ArticleRepositoryTest {

    @Autowired
    private ArticleRepository articleRepository;

    @Test
    void existsByLink_shouldReturnTrueWhenArticleExists() {
        Article article = new Article("http://example.com", "Title", "Content");
        articleRepository.save(article);

        boolean exists = articleRepository.existsByLink("http://example.com");

        assertThat(exists).isTrue();
    }

    @Test
    void existsByLink_shouldReturnFalseWhenArticleDoesNotExist() {
        boolean exists = articleRepository.existsByLink("http://nonexistent.com");

        assertThat(exists).isFalse();
    }
}
