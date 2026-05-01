package com.realnewsletter.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.AbstractBeansOfTypeDatabaseInitializerDetector;
import org.springframework.boot.sql.init.dependency.DatabaseInitializerDetector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Set;

/**
 * Manual Flyway configuration for Spring Boot 4.0.
 *
 * Spring Boot 4.0 removed Flyway auto-configuration (no FlywayAutoConfiguration exists).
 * This class:
 *  1. Creates a FlywayMigrationRunner bean that repairs + migrates the schema.
 *  2. Registers a DatabaseInitializerDetector so the JPA EntityManagerFactory
 *     automatically waits for Flyway to finish before validating entities.
 */
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfig {

    @Value("${spring.flyway.schemas:public}")
    private String schemas;

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String locations;

    @Value("${spring.flyway.baseline-on-migrate:true}")
    private boolean baselineOnMigrate;

    @Value("${spring.flyway.baseline-version:0}")
    private String baselineVersion;

    @Value("${spring.flyway.out-of-order:false}")
    private boolean outOfOrder;

    @Bean
    public FlywayMigrationRunner flywayMigrationRunner(DataSource dataSource) {
        return new FlywayMigrationRunner(dataSource, schemas, locations,
                baselineOnMigrate, baselineVersion, outOfOrder);
    }

    /**
     * Registers FlywayMigrationRunner as a database initializer so that
     * Spring Boot 4.0's JpaDependsOnDatabaseInitializationDetector makes
     * the EntityManagerFactory depend on it.
     */
    @Bean
    public static DatabaseInitializerDetector flywayDatabaseInitializerDetector() {
        return new AbstractBeansOfTypeDatabaseInitializerDetector() {
            @Override
            protected Set<Class<?>> getDatabaseInitializerBeanTypes() {
                return Set.of(FlywayMigrationRunner.class);
            }
        };
    }

    /**
     * Runs Flyway repair() then migrate() during application context initialization,
     * before the JPA EntityManagerFactory is created.
     */
    public static class FlywayMigrationRunner implements InitializingBean {

        private static final Logger log = LoggerFactory.getLogger(FlywayMigrationRunner.class);

        private final DataSource dataSource;
        private final String schemas;
        private final String locations;
        private final boolean baselineOnMigrate;
        private final String baselineVersion;
        private final boolean outOfOrder;

        public FlywayMigrationRunner(DataSource dataSource, String schemas, String locations,
                                     boolean baselineOnMigrate, String baselineVersion, boolean outOfOrder) {
            this.dataSource = dataSource;
            this.schemas = schemas;
            this.locations = locations;
            this.baselineOnMigrate = baselineOnMigrate;
            this.baselineVersion = baselineVersion;
            this.outOfOrder = outOfOrder;
        }

        @Override
        public void afterPropertiesSet() {
            log.info("Flyway: configuring migration (schemas={}, locations={}, baselineOnMigrate={}, baselineVersion={})",
                    schemas, locations, baselineOnMigrate, baselineVersion);

            try {
                Flyway flyway = Flyway.configure()
                        .dataSource(dataSource)
                        .schemas(schemas)
                        .locations(locations)
                        .baselineOnMigrate(baselineOnMigrate)
                        .baselineVersion(baselineVersion)
                        .outOfOrder(outOfOrder)
                        .load();

                log.info("Flyway: repairing schema history...");
                flyway.repair();

                log.info("Flyway: running migrations...");
                var result = flyway.migrate();

                log.info("Flyway: complete — {} migration(s) applied, target version: {}",
                        result.migrationsExecuted, result.targetSchemaVersion);
            } catch (Exception e) {
                log.error("Flyway: failed to connect to database or execute migrations. " +
                        "Please verify that DB_URL environment variable is set correctly. " +
                        "Error: {}", e.getMessage(), e);
                throw new RuntimeException("Flyway migration failed. Ensure DB_URL environment variable is set and database is accessible.", e);
            }
        }
    }
}
