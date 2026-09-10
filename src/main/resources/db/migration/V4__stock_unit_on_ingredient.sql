-- Move the stocking unit from each pantry row up to the ingredient, and give
-- ingredients a default shelf. A cook always keeps flour in grams, so the unit
-- describes the ingredient rather than one purchase of it. Location stays on
-- the pantry row, because the same ingredient can legitimately sit in two
-- places at once; the ingredient only supplies the starting choice.

ALTER TABLE ingredients ADD COLUMN stock_unit VARCHAR(20);
ALTER TABLE ingredients ADD COLUMN default_location VARCHAR(30);

-- Backfill from whatever the existing shelf rows already say.
UPDATE ingredients i
SET stock_unit = (
    SELECT p.unit FROM pantry_items p
    WHERE p.ingredient_id = i.id
    ORDER BY p.id
    LIMIT 1
);

UPDATE ingredients i
SET default_location = (
    SELECT p.location FROM pantry_items p
    WHERE p.ingredient_id = i.id
    ORDER BY p.id
    LIMIT 1
);

-- Ingredients that have never been stocked get sensible starting values.
UPDATE ingredients SET stock_unit = 'GRAM' WHERE stock_unit IS NULL;
UPDATE ingredients SET default_location = 'PANTRY' WHERE default_location IS NULL;

ALTER TABLE ingredients ALTER COLUMN stock_unit SET NOT NULL;
ALTER TABLE ingredients ALTER COLUMN default_location SET NOT NULL;

ALTER TABLE pantry_items DROP COLUMN unit;
