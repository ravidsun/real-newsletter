package com.realnewsletter.scheduler;

/**
 * Holds the outcome counters for a single ingestion run.
 * Shared by {@link NewsDataIngestionScheduler} and {@link NewsApiIngestionScheduler}.
 *
 * @param fetched total articles returned by the external API
 * @param saved   articles newly persisted to the database
 * @param skipped articles skipped (duplicates or missing link)
 * @param errors  articles that failed to save due to an exception
 */
public record IngestionResult(int fetched, int saved, int skipped, int errors) {}

