package com.realnewsletter.controller;

import com.realnewsletter.scheduler.IngestionResult;
import com.realnewsletter.scheduler.NewsApiIngestionScheduler;
import com.realnewsletter.scheduler.NewsDataIngestionScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller for manually triggering news ingestion jobs.
 *
 * <pre>
 *   POST /api/v1/ingestion?source=newsdata   – trigger Newsdata.io
 *   POST /api/v1/ingestion?source=newsapi    – trigger NewsAPI.org
 *   POST /api/v1/ingestion?source=all        – trigger all sources
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/ingestion")
@Tag(name = "Ingestion", description = "Manually trigger news ingestion from external sources")
public class IngestionController {

    private static final Logger logger = LoggerFactory.getLogger(IngestionController.class);

    private final NewsDataIngestionScheduler newsDataScheduler;
    private final NewsApiIngestionScheduler newsApiScheduler;

    public IngestionController(NewsDataIngestionScheduler newsDataScheduler,
                               NewsApiIngestionScheduler newsApiScheduler) {
        this.newsDataScheduler = newsDataScheduler;
        this.newsApiScheduler = newsApiScheduler;
    }

    /**
     * Triggers ingestion for the specified source.
     *
     * @param source one of: {@code newsdata}, {@code newsapi}, {@code all}
     */
    @PostMapping
    @Operation(
        summary = "Trigger ingestion by source",
        description = "Manually run ingestion for a specific source. " +
                      "Valid values: `newsdata`, `newsapi`, `all`."
    )
    public ResponseEntity<Map<String, Object>> trigger(
            @Parameter(description = "Source to ingest from: newsdata | newsapi | all", required = true)
            @RequestParam String source) {

        Instant start = Instant.now();
        logger.info("[IngestionController] Manual trigger: source={}", source);

        return switch (source.toLowerCase().trim()) {
            case "newsdata" -> runNewsData(start);
            case "newsapi"  -> runNewsApi(start);
            case "all"      -> runAll(start);
            default -> ResponseEntity.badRequest().body(error(
                    "Unknown source '" + source + "'. Valid values: newsdata, newsapi, all.", start));
        };
    }

    // ── private runners ───────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> runNewsData(Instant start) {
        try {
            IngestionResult result = newsDataScheduler.runIngestionNow();
            return ResponseEntity.ok(result("newsdata", "completed", start, result));
        } catch (Exception e) {
            logger.error("[IngestionController] Newsdata.io ingestion failed", e);
            return ResponseEntity.internalServerError()
                    .body(result("newsdata", "failed: " + e.getMessage(), start, null));
        }
    }

    private ResponseEntity<Map<String, Object>> runNewsApi(Instant start) {
        try {
            IngestionResult result = newsApiScheduler.runIngestionNow();
            return ResponseEntity.ok(result("newsapi", "completed", start, result));
        } catch (Exception e) {
            logger.error("[IngestionController] NewsAPI.org ingestion failed", e);
            return ResponseEntity.internalServerError()
                    .body(result("newsapi", "failed: " + e.getMessage(), start, null));
        }
    }

    private ResponseEntity<Map<String, Object>> runAll(Instant start) {
        Map<String, Object> sourceResults = new LinkedHashMap<>();

        try {
            IngestionResult r = newsDataScheduler.runIngestionNow();
            sourceResults.put("newsdata", resultStats("completed", r));
        } catch (Exception e) {
            logger.error("[IngestionController] Newsdata.io ingestion failed", e);
            sourceResults.put("newsdata", resultStats("failed: " + e.getMessage(), null));
        }

        try {
            IngestionResult r = newsApiScheduler.runIngestionNow();
            sourceResults.put("newsapi", resultStats("completed", r));
        } catch (Exception e) {
            logger.error("[IngestionController] NewsAPI.org ingestion failed", e);
            sourceResults.put("newsapi", resultStats("failed: " + e.getMessage(), null));
        }

        boolean anyFailed = sourceResults.values().stream()
                .map(v -> ((Map<?, ?>) v).get("status").toString())
                .anyMatch(s -> s.startsWith("failed"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", "all");
        body.put("triggeredAt", start.toString());
        body.put("elapsedMs", elapsed(start));
        body.put("sources", sourceResults);
        body.put("status", anyFailed ? "partial" : "completed");

        return anyFailed
                ? ResponseEntity.internalServerError().body(body)
                : ResponseEntity.ok(body);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> result(String source, String status, Instant start, IngestionResult r) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", source);
        body.put("status", status);
        body.put("triggeredAt", start.toString());
        body.put("elapsedMs", elapsed(start));
        if (r != null) {
            body.put("fetched", r.fetched());
            body.put("saved",   r.saved());
            body.put("skipped", r.skipped());
            body.put("errors",  r.errors());
        }
        return body;
    }

    /** Builds a per-source stats map used inside the "all" response. */
    private Map<String, Object> resultStats(String status, IngestionResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        if (r != null) {
            m.put("fetched", r.fetched());
            m.put("saved",   r.saved());
            m.put("skipped", r.skipped());
            m.put("errors",  r.errors());
        }
        return m;
    }

    private Map<String, Object> error(String message, Instant start) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("message", message);
        body.put("triggeredAt", start.toString());
        body.put("elapsedMs", elapsed(start));
        return body;
    }

    private long elapsed(Instant start) {
        return Instant.now().toEpochMilli() - start.toEpochMilli();
    }
}
