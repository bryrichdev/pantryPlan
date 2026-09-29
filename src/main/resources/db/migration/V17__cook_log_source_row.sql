-- Which pantry row each cooking deduction came from, so undoing a cook puts
-- the stock back where it was.
--
-- pantry_item_id points at the row itself. Cooking deletes a row it empties,
-- which clears this column, so the row's location and dates are copied as
-- well. Undo recreates the row from them when the original is gone. Logs
-- written before this migration have none of it and restore as a new row.

ALTER TABLE cook_logs ADD COLUMN pantry_item_id BIGINT REFERENCES pantry_items (id) ON DELETE SET NULL;
ALTER TABLE cook_logs ADD COLUMN source_location VARCHAR(30);
ALTER TABLE cook_logs ADD COLUMN source_purchased_on DATE;
ALTER TABLE cook_logs ADD COLUMN source_expires_on DATE;
