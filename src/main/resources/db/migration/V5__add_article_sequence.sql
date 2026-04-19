-- Create a sequence for the articles table
CREATE SEQUENCE IF NOT EXISTS articles_seq START 1 INCREMENT 1;

-- Add seq column populated automatically by the sequence
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS seq BIGINT NOT NULL DEFAULT nextval('articles_seq');

-- Always ensure the DEFAULT is set, even if the column already existed without one
ALTER TABLE articles ALTER COLUMN seq SET DEFAULT nextval('articles_seq');

-- Own the sequence so it's dropped with the table
ALTER SEQUENCE articles_seq OWNED BY articles.seq;
