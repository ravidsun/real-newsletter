-- Normalized title hash for cross-source duplicate detection.
-- SHA-256 hex of the lowercased, punctuation-stripped title.
-- Nullable so articles with no title are not constrained.
-- The unique index ignores NULLs (standard SQL / PostgreSQL behaviour).
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS title_hash VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS idx_articles_title_hash
    ON articles (title_hash)
    WHERE title_hash IS NOT NULL;

