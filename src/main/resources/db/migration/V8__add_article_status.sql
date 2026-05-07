-- Add the article lifecycle status column.
-- Default 'PUBLISHED' matches ArticleStatus.PUBLISHED — all existing articles remain visible.
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED';

