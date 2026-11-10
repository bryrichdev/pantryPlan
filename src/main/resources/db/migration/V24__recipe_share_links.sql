-- A public, read-only link to one recipe. Anyone with the link can view the
-- recipe without an account; the owner can stop sharing, or swap in a new
-- link so the old one stops working.
--
-- Unlike a group invite link, which grants membership and so is kept only as
-- a digest, this token is stored as it is. It grants no more than reading one
-- recipe, and keeping it lets the owner copy the link again whenever they like.
ALTER TABLE recipes ADD COLUMN share_token VARCHAR(43);
ALTER TABLE recipes ADD COLUMN shared_at TIMESTAMPTZ;

ALTER TABLE recipes ADD CONSTRAINT ck_recipes_share_token
    CHECK (share_token IS NULL OR share_token ~ '^[A-Za-z0-9_-]{43}$');

CREATE UNIQUE INDEX ux_recipes_share_token ON recipes (share_token) WHERE share_token IS NOT NULL;
