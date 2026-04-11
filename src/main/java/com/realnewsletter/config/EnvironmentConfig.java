package com.realnewsletter.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class that loads environment variables from .env file at startup.
 *
 * <p>Uses dotenv-java to load variables from .env.dev or .env file in the project root.
 * This allows the application to automatically configure database credentials and API keys
 * from environment files without requiring manual environment variable setup.
 */
@Configuration
public class EnvironmentConfig {

    static {
        // Load .env.dev file first (for development), then .env as fallback
        Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .ignoreIfMissing()
                .load();

        // Set system properties from loaded environment variables
        dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue())
        );
    }
}

