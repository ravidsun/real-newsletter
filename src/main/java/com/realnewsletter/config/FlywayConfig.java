package com.realnewsletter.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Flyway configuration to handle database migrations.
 * In development, clears the schema to ensure fresh migrations.
 */
@Configuration
public class FlywayConfig {

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    /**
     * For development profile only: clears and reinitializes the database schema
     * to ensure clean migrations from scratch.
     */
    @Bean
    @Profile("dev")
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            // Clean the database to remove any stale schema history
            flyway.clean();
            // Then migrate from scratch
            flyway.migrate();
        };
    }
}

