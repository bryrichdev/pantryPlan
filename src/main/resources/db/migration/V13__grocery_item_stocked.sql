-- When a bought grocery line was added to the pantry.
--
-- Ticking an item means "it is in the trolley". This records the later step of
-- putting it on the shelf, so the stock-up dialog can offer each line once and
-- a second submission cannot silently double the pantry.
--
-- Nullable: every existing line predates the feature and has not been stocked.

ALTER TABLE grocery_list_items ADD COLUMN stocked_at TIMESTAMPTZ;
