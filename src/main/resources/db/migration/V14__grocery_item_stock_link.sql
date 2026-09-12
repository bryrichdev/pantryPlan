-- What a stocked grocery line actually put on the shelf.
--
-- pantry_item_id is the row it created, so the cook can undo one line without
-- hunting for it on the pantry page. ON DELETE SET NULL because the pantry row
-- can be deleted on its own; the link goes, the grocery line stays.
--
-- stocked_quantity repeats the amount rather than reading it back through the
-- link, so the list can still say what was bought after the pantry row is gone
-- or its amount has been edited. It records what this line contributed.

ALTER TABLE grocery_list_items ADD COLUMN stocked_quantity NUMERIC(10, 3);

ALTER TABLE grocery_list_items ADD COLUMN pantry_item_id BIGINT
    REFERENCES pantry_items (id) ON DELETE SET NULL;

CREATE INDEX ix_grocery_list_items_pantry_item ON grocery_list_items (pantry_item_id);
