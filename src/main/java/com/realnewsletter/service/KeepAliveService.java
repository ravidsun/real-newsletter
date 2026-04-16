package com.realnewsletter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Periodically pings the application's own health endpoint to prevent
 * Render.com from spinning down the free-tier instance due to inactivity.
 */
@Service
public class KeepAliveService {

    private static final Logger log = LoggerFactory.getLogger(KeepAliveService.class);

    private final WebClient webClient;

    @Value("${app.keep-alive.url:https://real-newsletter.onrender.com/actuator/health}")
    private String healthUrl;

    public KeepAliveService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Fires every 10 minutes (600 000 ms). The initial delay of 2 minutes gives
     * the application time to fully start before the first ping.
     */
    @Scheduled(initialDelay = 120_000, fixedRate = 600_000)
    public void ping() {
        webClient.get()
                .uri(healthUrl)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(body -> log.info("Keep-alive ping OK → {}", healthUrl))
                .doOnError(ex -> log.warn("Keep-alive ping FAILED → {}: {}", healthUrl, ex.getMessage()))
                .onErrorComplete()
                .subscribe();
    }
}

