package com.realnewsletter.service;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Service;

/**
 * Service that sanitizes HTML content to prevent XSS attacks using the
 * OWASP Java HTML Sanitizer library.
 *
 * <p>Applied to all rich-text fields (title, description, content) submitted
 * via article create ({@code POST /api/v1/articles}) and update
 * ({@code PUT /api/v1/articles/{id}}) endpoints before persistence.</p>
 *
 * <p>Policy: allows only safe, formatted text — blocks scripts, iframes,
 * on-event handlers, and other JavaScript execution vectors. Image and link
 * tags are additionally permitted to support editorial formatting.</p>
 */
@Service
public class HtmlSanitizerService {

    /**
     * Safe HTML policy: formatted text + links + images.
     * Blocks all JavaScript execution vectors (scripts, on-event attributes, etc.).
     */
    private static final PolicyFactory POLICY =
            Sanitizers.FORMATTING
                    .and(Sanitizers.LINKS)
                    .and(Sanitizers.IMAGES)
                    .and(Sanitizers.BLOCKS);

    /**
     * Sanitizes the given HTML string, removing any elements or attributes
     * that could facilitate XSS.
     *
     * @param html the raw HTML string; may be {@code null}
     * @return the sanitized HTML string, or {@code null} when input is {@code null}
     */
    public String sanitize(String html) {
        if (html == null) {
            return null;
        }
        return POLICY.sanitize(html);
    }
}

