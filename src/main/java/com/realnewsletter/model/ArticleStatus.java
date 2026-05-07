package com.realnewsletter.model;

/**
 * Lifecycle status for an {@link Article}.
 *
 * <ul>
 *   <li>{@code DRAFT}     – created but not yet published to the public feed.</li>
 *   <li>{@code PUBLISHED} – visible in the public feed (default on ingestion).</li>
 *   <li>{@code DISABLED}  – hidden from the public feed by an admin; still in the DB.</li>
 *   <li>{@code ARCHIVED}  – automatically moved off the main feed after 7 days.</li>
 * </ul>
 */
public enum ArticleStatus {
    DRAFT,
    PUBLISHED,
    DISABLED,
    ARCHIVED
}

