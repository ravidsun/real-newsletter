package org.springframework.boot.autoconfigure.web.client;

/**
 * Shim class for Spring Boot 4 compatibility.
 * RestClientAutoConfiguration was removed in Spring Boot 4, but Spring AI 2.0.0-M2
 * still references it in bytecode annotations (@AutoConfigureAfter).
 * Java's annotation parser resolves these references at class-load time before
 * Spring can apply any exclusions, so this empty stub satisfies the reference.
 */
public class RestClientAutoConfiguration {
}

