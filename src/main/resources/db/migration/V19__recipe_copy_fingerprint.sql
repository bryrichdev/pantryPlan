-- Which shared recipe a copy was made from, as a fingerprint of its content.
--
-- A copy records a SHA-256 of the original's name, details, tags, and
-- ingredient lines at the moment it was copied. The unique index then lets an
-- account hold only one copy of any given version of a recipe, however many
-- groups it is shared into and whatever the copy was renamed to. If the owner
-- changes the recipe, its fingerprint changes and the new version can be
-- copied. Deleting a copy frees the fingerprint again.
--
-- Recipes written in the account itself, and every row that predates this,
-- have no fingerprint.

ALTER TABLE recipes ADD COLUMN copied_from_hash VARCHAR(64);

ALTER TABLE recipes ADD CONSTRAINT ck_recipes_copied_from_hash
    CHECK (copied_from_hash IS NULL OR copied_from_hash ~ '^[0-9a-f]{64}$');

CREATE UNIQUE INDEX uq_recipes_user_copied_from ON recipes (user_id, copied_from_hash)
    WHERE copied_from_hash IS NOT NULL;
