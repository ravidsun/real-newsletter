package com.realnewsletter.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    // All values are resolved from the active profile's application-{profile}.yml,
    // which merges on top of the base application.yml.  No hardcoded defaults here
    // so the yml files are the single authoritative source of truth.
    @Value("${spring.flyway.schemas}")
    private String schemas;

    @Value("${spring.flyway.locations}")
    private String locations;

    @Value("${spring.flyway.baseline-on-migrate}")
    private boolean baselineOnMigrate;

    @Value("${spring.flyway.baseline-version}")
    private String baselineVersion;

    @Value("${spring.flyway.out-of-order}")
    private boolean outOfOrder;

    @Value("${spring.flyway.create-schemas}")
    private boolean createSchemas;

    @Value("${spring.flyway.connect-retries}")
    private int connectRetries;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Bean
    public FlywayMigrationRunner flywayMigrationRunner(DataSource dataSource) {
        return new FlywayMigrationRunner(dataSource, schemas, locations,
                baselineOnMigrate, baselineVersion, outOfOrder, datasourceUrl,
                createSchemas, connectRetries);
    }

    /**
     * Forces the JPA EntityManagerFactory to depend on flywayMigrationRunner.
     *
     * Spring Boot 4.0 removed FlywayAutoConfiguration and the DatabaseInitializerDetector
     * bean approach is unreliable when the DatabaseInitializationDependencyConfigurer
     * is not loaded. This BeanDefinitionRegistryPostProcessor directly patches the
     * entityManagerFactory bean definition to declare an explicit dependsOn, guaranteeing
     * Flyway always runs and completes before Hibernate schema validation.
     */
    @Bean
    @SuppressWarnings("NullableProblems")
    public static BeanDefinitionRegistryPostProcessor enforceFlywayBeforeJpa() {
        return new BeanDefinitionRegistryPostProcessor() {
            @Override
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                // Spring Boot registers the EMF under this name via HibernateJpaConfiguration
                String[] emfCandidates = {"entityManagerFactory", "jpaSharedEM_entityManagerFactory"};
                for (String beanName : emfCandidates) {
                    if (registry.containsBeanDefinition(beanName)) {
                        BeanDefinition def = registry.getBeanDefinition(beanName);
                        String[] existing = def.getDependsOn();
                        List<String> deps = existing != null
                                ? new ArrayList<>(Arrays.asList(existing))
                                : new ArrayList<>();
                        if (!deps.contains("flywayMigrationRunner")) {
                            deps.add("flywayMigrationRunner");
                            def.setDependsOn(deps.toArray(new String[0]));
                        }
                    }
                }
            }

            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
                // nothing — dependency wiring is done in postProcessBeanDefinitionRegistry
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
        private final String datasourceUrl;
        private final boolean createSchemas;
        private final int connectRetries;

        public FlywayMigrationRunner(DataSource dataSource, String schemas, String locations,
                                     boolean baselineOnMigrate, String baselineVersion, boolean outOfOrder,
                                     String datasourceUrl, boolean createSchemas, int connectRetries) {
            this.dataSource = dataSource;
            this.schemas = schemas;
            this.locations = locations;
            this.baselineOnMigrate = baselineOnMigrate;
            this.baselineVersion = baselineVersion;
            this.outOfOrder = outOfOrder;
            this.datasourceUrl = datasourceUrl;
            this.createSchemas = createSchemas;
            this.connectRetries = connectRetries;
        }

        @Override
        public void afterPropertiesSet() {
            log.info("Flyway: configuring migration (schemas={}, locations={}, baselineOnMigrate={}, baselineVersion={})",
                    schemas, locations, baselineOnMigrate, baselineVersion);
            log.info("Flyway: database URL: {}", maskPassword(datasourceUrl));

            try {
                // Test the connection first to provide better error messages
                testDatabaseConnection();

                Flyway flyway = Flyway.configure()
                        .dataSource(dataSource)
                        .schemas(schemas)
                        .locations(locations)
                        .baselineOnMigrate(baselineOnMigrate)
                        .baselineVersion(baselineVersion)
                        .outOfOrder(outOfOrder)
                        .createSchemas(createSchemas)
                        .connectRetries(connectRetries)
                        .load();

                log.info("Flyway: repairing schema history...");
                flyway.repair();

                log.info("Flyway: running migrations...");
                var result = flyway.migrate();

                log.info("Flyway: complete — {} migration(s) applied, target version: {}",
                        result.migrationsExecuted, result.targetSchemaVersion);
            } catch (SQLException e) {
                String msg = "Flyway: database connection failed. " +
                        "Please verify that the database is accessible and the connection string is correct.\n" +
                        "Expected database URL format: " + maskPassword(datasourceUrl) + "\n" +
                        "Connection error: " + e.getMessage();
                log.error(msg, e);
                throw new RuntimeException(msg, e);
            } catch (Exception e) {
                log.error("Flyway: failed to execute migrations. Error: {}", e.getMessage(), e);
                throw new RuntimeException("Flyway migration failed. See logs for details.", e);
            }
        }

        /**
         * Tests database connectivity with a simple query to provide better error diagnostics.
         */
        private void testDatabaseConnection() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                conn.createStatement().execute("SELECT 1");
                log.info("Flyway: database connection verified successfully");
            } catch (SQLException e) {
                log.error("Flyway: unable to connect to database using URL: {}", maskPassword(datasourceUrl));
                throw e;
            }
        }

        /**
         * Masks the password in a JDBC URL for safe logging.
         */
        private static String maskPassword(String url) {
            return url.replaceAll("(password=)[^&;]*", "$1***");
        }
    }
}
