package com.realnewsletter.repository;

import com.realnewsletter.model.NewsdataArticle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ArticleRepositoryTest {

    @Autowired
    private ArticleRepository articleRepository;

    @BeforeEach
    void setUp() {
        articleRepository.deleteAll();
    }

    @Test
    void existsByLink_shouldReturnTrueWhenArticleExists() {
        NewsdataArticle article = new NewsdataArticle("http://example.com", "Title", "Content");
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
