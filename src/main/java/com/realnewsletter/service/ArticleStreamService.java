package com.realnewsletter.service;

import com.realnewsletter.dto.ArticleDto;
import com.realnewsletter.model.NewArticleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Broadcasts new articles to all connected SSE clients via Spring application events.
 */
@Service
public class ArticleStreamService {

    private static final Logger logger = LoggerFactory.getLogger(ArticleStreamService.class);

    private final List<SseEmitter> clients = new CopyOnWriteArrayList<>();

    /**
     * Registers a new SSE emitter. The caller (controller) is responsible for
     * returning the emitter to the HTTP response.
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        clients.add(emitter);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> clients.remove(emitter));
        emitter.onError(e -> clients.remove(emitter));
        logger.debug("SSE client connected. Total clients: {}", clients.size());
        return emitter;
    }

    /**
     * Listens for {@link NewArticleEvent} and broadcasts the new article to all connected clients.
     */
    @EventListener
    public void onNewArticle(NewArticleEvent event) {
        ArticleDto dto = ArticleDto.fromEntity(event.article());
        List<SseEmitter> deadClients = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : clients) {
            try {
                emitter.send(SseEmitter.event()
                        .data(dto)
                        .id(dto.id() != null ? dto.id().toString() : "")
                        .name("new-article"));
            } catch (IOException e) {
                logger.warn("SSE client disconnected, removing: {}", e.getMessage());
                deadClients.add(emitter);
            }
        }
        clients.removeAll(deadClients);
        logger.debug("Broadcast new-article event to {} clients", clients.size() - deadClients.size());
    }

    /**
     * Returns the current number of connected SSE clients (for testing/monitoring).
     */
    public int getClientCount() {
        return clients.size();
    }
}

