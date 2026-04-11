-- Rename 'url' to 'link' to match Newsdata.io schema
ALTER TABLE articles RENAME COLUMN url TO link;

-- Drop old tags column (replaced by individual ai_tag column)
ALTER TABLE articles DROP COLUMN IF EXISTS tags;

-- Add all Newsdata.io API fields
ALTER TABLE articles
    ADD COLUMN article_id    TEXT,
    ADD COLUMN description   TEXT,
    ADD COLUMN keywords      TEXT,
    ADD COLUMN creator       TEXT,
    ADD COLUMN language      TEXT,
    ADD COLUMN country       TEXT,
    ADD COLUMN category      TEXT,
    ADD COLUMN datatype      TEXT,
    ADD COLUMN pub_date      TIMESTAMP WITH TIME ZONE,
    ADD COLUMN pub_date_tz   TEXT,
    ADD COLUMN fetched_at    TIMESTAMP WITH TIME ZONE,
    ADD COLUMN image_url     TEXT,
    ADD COLUMN video_url     TEXT,
    ADD COLUMN source_id     TEXT,
    ADD COLUMN source_name   TEXT,
    ADD COLUMN source_priority INTEGER,
    ADD COLUMN source_url    TEXT,
    ADD COLUMN source_icon   TEXT,
    ADD COLUMN sentiment     TEXT,
    ADD COLUMN sentiment_stats TEXT,
    ADD COLUMN ai_tag        TEXT,
    ADD COLUMN ai_region     TEXT,
    ADD COLUMN ai_org        TEXT,
    ADD COLUMN is_duplicate  BOOLEAN;

