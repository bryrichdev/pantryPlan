-- A usual amount to stock, kept on the ingredient beside its default location.
-- It is measured in the ingredient's stock unit: 12 is a dozen eggs when the
-- unit is PIECE, and 5 is a 5 lb bag of flour kept in pounds. Like the default
-- location, it only prefills a new pantry row.
--
-- Nullable, because some ingredients have no single usual amount. NUMERIC to
-- match pantry_items.quantity, since the value is copied into that column and
-- units such as POUND and CUP need fractions.

ALTER TABLE ingredients ADD COLUMN default_quantity NUMERIC(10, 3);

ALTER TABLE ingredients ADD CONSTRAINT ck_ingredients_default_quantity_positive
    CHECK (default_quantity IS NULL OR default_quantity > 0);

-- The preset catalogue carries the same column, so an import brings it across.

ALTER TABLE ingredient_presets ADD COLUMN default_quantity NUMERIC(10, 3);

ALTER TABLE ingredient_presets ADD CONSTRAINT ck_ingredient_presets_default_quantity
    CHECK (default_quantity IS NULL OR default_quantity > 0);

-- Move the presets to US units and seed common US package sizes in those units.
-- Weight goes to pounds for bulk staples and meat, ounces for everything sold
-- in smaller packages. Volume goes to cups, except vanilla, which recipes
-- measure by the teaspoon. Counted items stay in pieces.
--
-- grams_per_cup is unchanged. It is a density, and the conversion service
-- applies it to whatever unit the ingredient is stocked in.
--
-- A null amount is deliberate: meat sold by variable weight, produce bought
-- loose, and parsley, whose bunches vary too much.

UPDATE ingredient_presets p
SET stock_unit       = v.stock_unit,
    default_quantity = v.quantity,
    updated_at       = NOW()
FROM (VALUES
          -- Baking staples
          ('All-purpose flour',     'POUND',     5),     -- 5 lb bag
          ('Bread flour',           'POUND',     5),     -- 5 lb bag
          ('Whole wheat flour',     'POUND',     5),     -- 5 lb bag
          ('Granulated sugar',      'POUND',     4),     -- 4 lb bag
          ('Brown sugar',           'POUND',     2),     -- 2 lb bag
          ('Powdered sugar',        'POUND',     2),     -- 2 lb bag
          ('Cornstarch',            'OUNCE',     16),    -- 16 oz box
          ('Cocoa powder',          'OUNCE',     8),     -- 8 oz tin
          ('Baking soda',           'OUNCE',     16),    -- 16 oz box
          ('Baking powder',         'OUNCE',     8.1),   -- 8.1 oz can
          ('Active dry yeast',      'OUNCE',     4),     -- 4 oz jar
          ('Rolled oats',           'OUNCE',     18),    -- 18 oz canister
          ('Breadcrumbs',           'OUNCE',     15),    -- 15 oz canister
          ('Chocolate chips',       'OUNCE',     12),    -- 12 oz bag

          -- Grains and dried goods
          ('White rice',            'POUND',     2),     -- 2 lb bag
          ('Brown rice',            'POUND',     2),     -- 2 lb bag
          ('Quinoa',                'OUNCE',     12),    -- 12 oz bag
          ('Couscous',              'OUNCE',     10),    -- 10 oz box
          ('Dried lentils',         'POUND',     1),     -- 1 lb bag
          ('Dried black beans',     'POUND',     1),     -- 1 lb bag
          ('Spaghetti',             'OUNCE',     16),    -- 16 oz box
          ('Elbow pasta',           'OUNCE',     16),    -- 16 oz box
          ('Canned diced tomatoes', 'OUNCE',     14.5),  -- 14.5 oz can
          ('Canned black beans',    'OUNCE',     15),    -- 15 oz can
          ('Chicken stock',         'CUP',       4),     -- 32 fl oz carton

          -- Fats and liquids
          ('Olive oil',             'CUP',       2),     -- 16 fl oz bottle
          ('Vegetable oil',         'CUP',       6),     -- 48 fl oz bottle
          ('Honey',                 'OUNCE',     12),    -- 12 oz bottle
          ('Maple syrup',           'CUP',       1.5),   -- 12 fl oz bottle
          ('Soy sauce',             'CUP',       1.25),  -- 10 fl oz bottle
          ('White vinegar',         'CUP',       4),     -- 32 fl oz bottle
          ('Peanut butter',         'OUNCE',     16),    -- 16 oz jar

          -- Dairy
          ('Butter',                'OUNCE',     16),    -- 1 lb, four sticks
          ('Whole milk',            'CUP',       16),    -- 1 gallon
          ('Heavy cream',           'CUP',       2),     -- 1 pint
          ('Plain yogurt',          'OUNCE',     32),    -- 32 oz tub
          ('Sour cream',            'OUNCE',     16),    -- 16 oz tub
          ('Shredded cheddar',      'OUNCE',     8),     -- 8 oz bag
          ('Grated parmesan',       'OUNCE',     8),     -- 8 oz container
          ('Cream cheese',          'OUNCE',     8),     -- 8 oz block
          ('Eggs',                  'PIECE',     12),    -- one dozen

          -- Meat
          ('Chicken breast',        'POUND',     NULL),  -- packs vary in weight
          ('Chicken thighs',        'POUND',     NULL),  -- packs vary in weight
          ('Ground beef',           'POUND',     1),     -- 1 lb pack
          ('Bacon',                 'OUNCE',     12),    -- 12 oz pack
          ('Italian sausage',       'POUND',     1),     -- 1 lb pack

          -- Produce
          ('Yellow onion',          'PIECE',     NULL),  -- bought loose
          ('Garlic clove',          'PIECE',     10),    -- one head
          ('Carrot',                'PIECE',     NULL),  -- bought loose
          ('Celery stalk',          'PIECE',     8),     -- one bunch
          ('Yukon potato',          'POUND',     3),     -- 3 lb bag
          ('Roma tomato',           'PIECE',     NULL),  -- bought loose
          ('Lemon',                 'PIECE',     NULL),  -- bought loose
          ('Fresh basil',           'OUNCE',     0.75),  -- 0.75 oz clamshell
          ('Fresh parsley',         'OUNCE',     NULL),  -- bunches vary too much
          ('Baby spinach',          'OUNCE',     5),     -- 5 oz clamshell

          -- Spices
          ('Table salt',            'OUNCE',     26),    -- 26 oz canister
          ('Kosher salt',           'POUND',     3),     -- 3 lb box
          ('Black pepper',          'OUNCE',     3),     -- 3 oz tin
          ('Ground cumin',          'OUNCE',     1.5),   -- 1.5 oz jar
          ('Paprika',               'OUNCE',     2.12),  -- 2.12 oz jar
          ('Chili powder',          'OUNCE',     2.5),   -- 2.5 oz jar
          ('Dried oregano',         'OUNCE',     0.75),  -- 0.75 oz jar
          ('Ground cinnamon',       'OUNCE',     2.37),  -- 2.37 oz jar
          ('Vanilla extract',       'TEASPOON',  12),    -- 2 fl oz bottle

          -- Frozen and bakery
          ('Frozen peas',           'OUNCE',     12),    -- 12 oz bag
          ('Frozen corn',           'OUNCE',     12),    -- 12 oz bag
          ('Sandwich bread',        'PIECE',     20),    -- one loaf of slices
          ('Flour tortillas',       'PIECE',     10)     -- one pack
     ) AS v (name, stock_unit, quantity)
WHERE p.name = v.name;

-- Rows are matched by name, so a misspelling above updates nothing. Every
-- weighed or measured preset changes unit, so any still in a metric unit means
-- a name did not match. Failing here rolls back the whole migration.

DO $$
    BEGIN
        IF EXISTS (
            SELECT 1 FROM ingredient_presets
            WHERE stock_unit IN ('GRAM', 'KILOGRAM', 'MILLILITER', 'LITER')
        ) THEN
            RAISE EXCEPTION 'V6: a preset is still in a metric unit; check the names in the VALUES list';
        END IF;
    END $$;