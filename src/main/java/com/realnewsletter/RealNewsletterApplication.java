package com.realnewsletter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Real Newsletter application.
 *
 * <p>{@code @EnableScheduling} activates Spring's task-scheduling infrastructure,
 * required by subsequent issues that implement periodic article-fetching jobs.</p>
 */
@SpringBootApplication
@EnableScheduling
public class RealNewsletterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealNewsletterApplication.class, args);
    }
}

