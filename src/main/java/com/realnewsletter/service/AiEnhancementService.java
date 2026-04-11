package com.realnewsletter.service;

import com.realnewsletter.model.Article;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Service that uses Spring AI (OpenAI) to enrich ingested articles with an
 * AI-generated summary and a set of comma-separated tags.
 */
@Service
public class AiEnhancementService {

    private static final Logger logger = LoggerFactory.getLogger(AiEnhancementService.class);

    private final ChatClient chatClient;

    public AiEnhancementService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * Calls the AI model to generate a summary and tags for the given article.
     * Fields are set directly on the article object.
     *
     * @param article the article to enrich; modified in-place
     */
    public void enrichArticle(Article article) {
        String prompt = "Summarize the following article and suggest a few tags (comma-separated):\n\n"
                + article.getContent();

        String output = chatClient.prompt()
                .system("You are an AI assistant that summarizes articles and generates tags.")
                .user(prompt)
                .call()
                .content();

        article.setAiSummary(extractSummary(output));
        article.setTags(parseTags(output));
        logger.debug("AI enrichment applied to article: {}", article.getUrl());
    }

    /**
     * Extracts the summary portion of the AI output.
     * Text appearing before a "Tags:" line is treated as the summary.
     */
    String extractSummary(String output) {
        if (output == null) return null;
        int tagsIdx = output.toLowerCase().indexOf("tags:");
        if (tagsIdx > 0) {
            return output.substring(0, tagsIdx).trim();
        }
        return output.trim();
    }

    /**
     * Extracts the comma-separated tags portion of the AI output.
     * Returns the text after a "Tags:" marker, or {@code null} if none found.
     */
    String parseTags(String output) {
        if (output == null) return null;
        int tagsIdx = output.toLowerCase().indexOf("tags:");
        if (tagsIdx >= 0) {
            return output.substring(tagsIdx + 5).trim();
        }
        return null;
    }
}

