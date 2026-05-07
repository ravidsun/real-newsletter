-- Add discriminator column for Hibernate single-table inheritance.
-- Identifies which news source each article originates from.
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(50) NOT NULL DEFAULT 'NEWSDATA';

-- Remove the default after back-filling existing rows.
ALTER TABLE articles ALTER COLUMN source_type DROP DEFAULT;
