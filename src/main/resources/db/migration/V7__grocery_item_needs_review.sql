-- Marks a grocery line whose recipe unit could not be converted into the
-- ingredient's stock unit, such as cups of something kept in pounds with no
-- weight per cup. The row holds the amount in the recipe's own unit and is not
-- subtracted from the pantry, because the two amounts cannot be compared.

ALTER TABLE grocery_list_items ADD COLUMN needs_review BOOLEAN NOT NULL DEFAULT FALSE;
