-- Where a recipe came from: a blog post, a video, a newspaper site. Optional.
-- Only web addresses are kept, so the recipe page can always link to it safely.
ALTER TABLE recipes ADD COLUMN source_url varchar(500);
ALTER TABLE recipes ADD CONSTRAINT recipes_source_url_is_web
    CHECK (source_url IS NULL OR source_url ~* '^https?://');
