-- Rename 'url' to 'link' to match Newsdata.io schema (only if 'url' still exists)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'articles' AND column_name = 'url') THEN
        ALTER TABLE articles RENAME COLUMN url TO link;
    END IF;
END $$;

-- Drop old tags column (replaced by individual ai_tag column)
ALTER TABLE articles DROP COLUMN IF EXISTS tags;

-- Add all Newsdata.io API fields
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS article_id      TEXT,
    ADD COLUMN IF NOT EXISTS link            TEXT,
    ADD COLUMN IF NOT EXISTS description     TEXT,
    ADD COLUMN IF NOT EXISTS keywords        TEXT,
    ADD COLUMN IF NOT EXISTS creator         TEXT,
    ADD COLUMN IF NOT EXISTS language        TEXT,
    ADD COLUMN IF NOT EXISTS country         TEXT,
    ADD COLUMN IF NOT EXISTS category        TEXT,
    ADD COLUMN IF NOT EXISTS datatype        TEXT,
    ADD COLUMN IF NOT EXISTS pub_date        TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS pub_date_tz     TEXT,
    ADD COLUMN IF NOT EXISTS fetched_at      TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS image_url       TEXT,
    ADD COLUMN IF NOT EXISTS video_url       TEXT,
    ADD COLUMN IF NOT EXISTS source_id       TEXT,
    ADD COLUMN IF NOT EXISTS source_name     TEXT,
    ADD COLUMN IF NOT EXISTS source_priority INTEGER,
    ADD COLUMN IF NOT EXISTS source_url      TEXT,
    ADD COLUMN IF NOT EXISTS source_icon     TEXT,
    ADD COLUMN IF NOT EXISTS sentiment       TEXT,
    ADD COLUMN IF NOT EXISTS sentiment_stats TEXT,
    ADD COLUMN IF NOT EXISTS ai_tag          TEXT,
    ADD COLUMN IF NOT EXISTS ai_region       TEXT,
    ADD COLUMN IF NOT EXISTS ai_org          TEXT,
    ADD COLUMN IF NOT EXISTS is_duplicate    BOOLEAN;
