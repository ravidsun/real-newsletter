package com.realnewsletter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: verifies that the Spring application context loads without errors.
 *
 * <p>Uses the {@code test} profile which switches to an H2 in-memory database
 * and disables Flyway, so no external Supabase connection is required.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class RealNewsletterApplicationTests {

    /**
     * If the application context fails to start for any reason this test will
     * fail, giving immediate feedback about broken configuration or wiring.
     */
    @Test
    void contextLoads() {
        // No assertions needed; the test passes if the context starts successfully.
    }
}

