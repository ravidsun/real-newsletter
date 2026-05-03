package com.realnewsletter.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class that loads environment variables from a profile-specific .env file at startup.
 *
 * <p>Resolution order (first found wins):
 * <ol>
 *   <li>{@code .env.<profile>} – e.g. {@code .env.dev} or {@code .env.prd}</li>
 *   <li>{@code .env} – generic fallback</li>
 * </ol>
 *
 * <p>The active profile is read from the {@code SPRING_PROFILES_ACTIVE} environment variable
 * or the {@code spring.profiles.active} system property (set e.g. via {@code -Dspring.profiles.active=dev}).
 * This runs in a {@code static} initializer so that all database/API-key properties are
 * available before Spring's {@code Environment} is fully wired.
 */
@Configuration
public class EnvironmentConfig {

    static {
        String profile = resolveActiveProfile();
        boolean loaded = false;

        // 1. Try profile-specific file: .env.dev / .env.prd / etc.
        if (profile != null && !profile.isBlank()) {
            try {
                Dotenv profileDotenv = Dotenv.configure()
                        .directory(".")
                        .filename(".env." + profile)
                        .ignoreIfMissing()
                        .load();
                if (!profileDotenv.entries().isEmpty()) {
                    profileDotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));
                    loaded = true;
                }
            } catch (Exception ignored) {
                // file not present – fall through to generic .env
            }
        }

        // 2. Fall back to generic .env
        if (!loaded) {
            Dotenv dotenv = Dotenv.configure()
                    .directory(".")
                    .ignoreIfMissing()
                    .load();
            dotenv.entries().forEach(e -> System.setProperty(e.getKey(), e.getValue()));
        }
    }

    /**
     * Reads {@code SPRING_PROFILES_ACTIVE} from the OS environment first,
     * then from a Java system property, returning the first profile found.
     */
    private static String resolveActiveProfile() {
        // OS env var takes priority (set by docker-compose / CI / container runtimes)
        String env = System.getenv("SPRING_PROFILES_ACTIVE");
        if (env != null && !env.isBlank()) {
            // support comma-separated list – take the first entry
            return env.split(",")[0].trim();
        }
        // Fall back to -Dspring.profiles.active JVM arg
        String prop = System.getProperty("spring.profiles.active");
        if (prop != null && !prop.isBlank()) {
            return prop.split(",")[0].trim();
        }
        return null;
    }
}

