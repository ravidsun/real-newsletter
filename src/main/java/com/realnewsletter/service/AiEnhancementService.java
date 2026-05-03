package com.realnewsletter.service;

import com.realnewsletter.model.Article;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service that uses Spring AI (Groq / OpenAI-compatible) to enrich ingested articles with an
 * AI-generated summary and a set of comma-separated tags.
 *
 * <p>Rate-limiting strategy (two layers):
 * <ol>
 *   <li><b>Proactive throttle</b> – a configurable {@code ai.enrichment.inter-call-delay-ms} sleep
 *       is inserted before every API call to keep the request rate below the provider's RPM quota.
 *       Default: 2 100 ms (safe for Groq free tier at 30 RPM).</li>
 *   <li><b>Reactive retry</b> – {@code spring.ai.retry.on-http-codes: 429} makes Spring AI treat
 *       HTTP 429 responses as transient and retry them with exponential back-off.</li>
 * </ol>
 * </p>
 */
@Service
public class AiEnhancementService {

    private static final Logger logger = LoggerFactory.getLogger(AiEnhancementService.class);

    private final ChatClient chatClient;
    private final long interCallDelayMs;

    public AiEnhancementService(ChatClient.Builder builder,
                                @Value("${ai.enrichment.inter-call-delay-ms:0}") long interCallDelayMs) {
        this.chatClient = builder.build();
        this.interCallDelayMs = interCallDelayMs;
    }

    /**
     * Calls the AI model to generate a summary and tags for the given article.
     * Fields are set directly on the article object.
     *
     * @param article the article to enrich; modified in-place
     */
    public void enrichArticle(Article article) {
        throttle();

        String prompt = "Summarize the following article and suggest a few tags (comma-separated):\n\n"
                + article.getContent();

        String output = chatClient.prompt()
                .system("You are an AI assistant that summarizes articles and generates tags.")
                .user(prompt)
                .call()
                .content();

        article.setAiSummary(extractSummary(output));
        article.setAiTag(parseTags(output));
        logger.debug("AI enrichment applied to article: {}", article.getLink());
    }

    /**
     * Sleeps for {@code interCallDelayMs} milliseconds to proactively stay within the
     * provider's RPM quota.  No-op when the delay is configured to 0.
     */
    private void throttle() {
        if (interCallDelayMs <= 0) return;
        try {
            Thread.sleep(interCallDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("AI enrichment throttle interrupted");
        }
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

