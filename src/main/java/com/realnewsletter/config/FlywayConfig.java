package com.realnewsletter.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Flyway configuration to handle database migrations.
 * In development, clears the schema to ensure fresh migrations.
 */
@Configuration
public class FlywayConfig {

    /**
     * For development profile only: clears and reinitializes the database schema
     * to ensure clean migrations from scratch.
     */
    @Bean
    @Profile("dev")
    public ApplicationRunner flywayCleanMigrate(ObjectProvider<Flyway> flywayProvider) {
        return args -> {
            Flyway flyway = flywayProvider.getIfAvailable();
            if (flyway != null) {
                flyway.clean();
                flyway.migrate();
            }
        };
    }
}
