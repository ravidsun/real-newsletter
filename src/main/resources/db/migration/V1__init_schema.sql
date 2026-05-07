-- Enable the uuid-ossp extension so uuid_generate_v4() is available.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Core articles table.  Each row represents a single news article that has
-- been fetched from an external source.
CREATE TABLE IF NOT EXISTS articles (
    id         UUID                     PRIMARY KEY DEFAULT uuid_generate_v4(),
    url        TEXT                     NOT NULL UNIQUE,
    title      TEXT,
    content    TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);
