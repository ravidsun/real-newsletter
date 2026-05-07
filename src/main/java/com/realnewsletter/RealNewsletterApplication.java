package com.realnewsletter;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Real Newsletter application.
 *
 * <p>{@code @EnableScheduling} activates Spring's task-scheduling infrastructure,
 * required by subsequent issues that implement periodic article-fetching jobs.</p>
 *
 * <p>{@code @ConfigurationPropertiesScan} registers all {@code @ConfigurationProperties}
 * classes in the package (e.g. {@code RateLimitProperties}) as Spring beans.</p>
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class RealNewsletterApplication {

    public static void main(String[] args) {
        // Load environment variables from .env files
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        // Populate System properties with loaded values (optional but helpful)
        dotenv.entries().forEach(entry ->
            System.setProperty(entry.getKey(), entry.getValue())
        );

        SpringApplication.run(RealNewsletterApplication.class, args);
    }
}

