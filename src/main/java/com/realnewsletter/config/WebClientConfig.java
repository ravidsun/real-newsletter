package com.realnewsletter.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Spring configuration class that provides a shared {@link WebClient} bean
 * pre-configured with sensible connection and response timeouts.
 *
 * <p>Using a single shared bean avoids the overhead of creating a new HTTP
 * connection pool per injection point and makes timeout behaviour easy to
 * test and reason about in one place.</p>
 */
@Configuration
public class WebClientConfig {

    /**
     * Creates a {@link WebClient} instance backed by a Reactor Netty
     * {@link HttpClient} with:
     * <ul>
     *   <li>5-second TCP connection timeout</li>
     *   <li>5-second response timeout (time to first byte)</li>
     * </ul>
     *
     * @param builder the auto-configured {@link WebClient.Builder} injected by Spring Boot
     * @return the shared WebClient bean
     */
    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(5));

        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}

