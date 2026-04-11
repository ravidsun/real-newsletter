package com.realnewsletter.service;

import com.realnewsletter.model.Article;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Answers;

import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiEnhancementServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private ChatClient.Builder builder;

    private AiEnhancementService service;

    @BeforeEach
    void setUp() {
        when(builder.build()).thenReturn(chatClient);
        service = new AiEnhancementService(builder);
    }

    @Test
    void enrichArticle_shouldSetAiSummaryAndTagsWhenResponseContainsTagsSection() {
        Article article = new Article("http://example.com", "Title", "Content about AI");
        String response = "This article discusses artificial intelligence and its impact.\n\nTags: ai, technology, innovation";

        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .call()
                .content())
                .thenReturn(response);

        service.enrichArticle(article);

        assertThat(article.getAiSummary()).contains("artificial intelligence");
        assertThat(article.getAiTag()).contains("ai");
        assertThat(article.getAiTag()).contains("technology");
    }

    @Test
    void enrichArticle_shouldSetFullResponseAsSummaryWhenNoTagsSection() {
        Article article = new Article("http://example.com", "Title", "Some content");
        String response = "A brief summary without a tags section.";

        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .call()
                .content())
                .thenReturn(response);

        service.enrichArticle(article);

        assertThat(article.getAiSummary()).isEqualTo("A brief summary without a tags section.");
        assertThat(article.getAiTag()).isNull();
    }

    @Test
    void enrichArticle_shouldHandleNullOutputGracefully() {
        Article article = new Article("http://example.com", "Title", "Content");

        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .call()
                .content())
                .thenReturn(null);

        // Should not throw any exception
        service.enrichArticle(article);

        assertThat(article.getAiSummary()).isNull();
        assertThat(article.getAiTag()).isNull();
    }

    @Test
    void extractSummary_shouldReturnTextBeforeTagsLabel() {
        String output = "Short summary.\n\nTags: news, world";
        assertThat(service.extractSummary(output)).isEqualTo("Short summary.");
    }

    @Test
    void extractSummary_shouldReturnFullOutputWhenNoTagsLabel() {
        String output = "Just a summary.";
        assertThat(service.extractSummary(output)).isEqualTo("Just a summary.");
    }

    @Test
    void extractSummary_shouldReturnNullForNullInput() {
        assertThat(service.extractSummary(null)).isNull();
    }

    @Test
    void parseTags_shouldExtractTagsAfterTagsLabel() {
        String output = "Summary text.\n\nTags: java, spring, ai";
        assertThat(service.parseTags(output)).isEqualTo("java, spring, ai");
    }

    @Test
    void parseTags_shouldReturnNullWhenNoTagsLabel() {
        String output = "Summary without tags.";
        assertThat(service.parseTags(output)).isNull();
    }

    @Test
    void parseTags_shouldReturnNullForNullInput() {
        assertThat(service.parseTags(null)).isNull();
    }
}

