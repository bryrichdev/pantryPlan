-- A usual amount to stock, kept on the ingredient beside its default location.
-- It is measured in the ingredient's stock unit: 12 is a dozen eggs when the
-- unit is PIECE, and 2268 is a 5 lb bag of flour kept in grams. Like the
-- default location, it only prefills a new pantry row.
--
-- Nullable, because some ingredients have no single usual amount. NUMERIC to
-- match pantry_items.quantity, since the value is copied into that column and
-- units such as POUND and LITER need fractions.

ALTER TABLE ingredients ADD COLUMN default_quantity NUMERIC(10, 3);

ALTER TABLE ingredients ADD CONSTRAINT ck_ingredients_default_quantity_positive
    CHECK (default_quantity IS NULL OR default_quantity > 0);

-- The preset catalogue carries the same column, so an import brings it across.

ALTER TABLE ingredient_presets ADD COLUMN default_quantity NUMERIC(10, 3);

ALTER TABLE ingredient_presets ADD CONSTRAINT ck_ingredient_presets_default_quantity
    CHECK (default_quantity IS NULL OR default_quantity > 0);

-- Common US package sizes, converted into each preset's stock unit and rounded
-- to whole grams or millilitres. Left null on purpose: meat sold by variable
-- weight (chicken breast, chicken thighs), produce bought loose (onion,
-- carrot, Roma tomato, lemon), and parsley, whose bunches vary too much.
--
-- Matched by name, so a misspelling here updates nothing. IngredientPresetTests
-- counts the seeded rows to catch that.

UPDATE ingredient_presets p
SET default_quantity = v.quantity,
    updated_at       = NOW()
FROM (VALUES
    -- Baking staples
    ('All-purpose flour',     2268),  -- 5 lb bag
    ('Bread flour',           2268),  -- 5 lb bag
    ('Whole wheat flour',     2268),  -- 5 lb bag
    ('Granulated sugar',      1814),  -- 4 lb bag
    ('Brown sugar',           907),   -- 2 lb bag
    ('Powdered sugar',        907),   -- 2 lb bag
    ('Cornstarch',            454),   -- 16 oz box
    ('Cocoa powder',          227),   -- 8 oz tin
    ('Baking soda',           454),   -- 16 oz box
    ('Baking powder',         230),   -- 8.1 oz can
    ('Active dry yeast',      113),   -- 4 oz jar
    ('Rolled oats',           510),   -- 18 oz canister
    ('Breadcrumbs',           425),   -- 15 oz canister
    ('Chocolate chips',       340),   -- 12 oz bag

    -- Grains and dried goods
    ('White rice',            907),   -- 2 lb bag
    ('Brown rice',            907),   -- 2 lb bag
    ('Quinoa',                454),   -- 16 oz bag
    ('Couscous',              283),   -- 10 oz box
    ('Dried lentils',         454),   -- 1 lb bag
    ('Dried black beans',     454),   -- 1 lb bag
    ('Spaghetti',             454),   -- 1 lb box
    ('Elbow pasta',           454),   -- 1 lb box
    ('Canned diced tomatoes', 411),   -- 14.5 oz can
    ('Canned black beans',    425),   -- 15 oz can
    ('Chicken stock',         946),   -- 32 fl oz carton

    -- Fats and liquids
    ('Olive oil',             500),   -- 500 ml bottle
    ('Vegetable oil',         1420),  -- 48 fl oz bottle
    ('Honey',                 340),   -- 12 oz bottle
    ('Maple syrup',           355),   -- 12 fl oz bottle
    ('Soy sauce',             444),   -- 15 fl oz bottle
    ('White vinegar',         946),   -- 32 fl oz bottle
    ('Peanut butter',         454),   -- 16 oz jar

    -- Dairy
    ('Butter',                454),   -- 1 lb, four sticks
    ('Whole milk',            3785),  -- 1 gallon
    ('Heavy cream',           473),   -- 1 pint
    ('Plain yogurt',          907),   -- 32 oz tub
    ('Sour cream',            454),   -- 16 oz tub
    ('Shredded cheddar',      227),   -- 8 oz bag
    ('Grated parmesan',       227),   -- 8 oz container
    ('Cream cheese',          227),   -- 8 oz block
    ('Eggs',                  12),    -- one dozen

    -- Meat
    ('Ground beef',           454),   -- 1 lb pack
    ('Bacon',                 340),   -- 12 oz pack
    ('Italian sausage',       454),   -- 1 lb pack

    -- Produce
    ('Garlic clove',          10),    -- one head
    ('Celery stalk',          8),     -- one bunch
    ('Yukon potato',          1361),  -- 3 lb bag
    ('Fresh basil',           21),    -- 0.75 oz clamshell
    ('Baby spinach',          142),   -- 5 oz clamshell

    -- Spices, by the jar
    ('Table salt',            737),   -- 26 oz canister
    ('Kosher salt',           1361),  -- 3 lb box
    ('Black pepper',          85),    -- 3 oz tin
    ('Ground cumin',          43),    -- 1.5 oz jar
    ('Paprika',               60),    -- 2.12 oz jar
    ('Chili powder',          71),    -- 2.5 oz jar
    ('Dried oregano',         21),    -- 0.75 oz jar
    ('Ground cinnamon',       67),    -- 2.37 oz jar
    ('Vanilla extract',       59),    -- 2 fl oz bottle

    -- Frozen and bakery
    ('Frozen peas',           340),   -- 12 oz bag
    ('Frozen corn',           340),   -- 12 oz bag
    ('Sandwich bread',        20),    -- one loaf of slices
    ('Flour tortillas',       10)     -- one pack
) AS v (name, quantity)
WHERE p.name = v.name;
