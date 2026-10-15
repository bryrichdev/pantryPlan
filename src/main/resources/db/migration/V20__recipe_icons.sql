-- Recipe pictures: an optional small drawing beside a recipe's name, so it
-- is quick to spot in a list — what the dish is, or what it is cooked in.
-- Looks only: nothing in planning, shopping, or copy fingerprints reads it.
--
-- The allowed names match RecipeIcon; a new picture needs a new migration.

ALTER TABLE recipes ADD COLUMN icon VARCHAR(20);
ALTER TABLE recipes ADD CONSTRAINT ck_recipes_icon
    CHECK (icon IS NULL OR icon IN ('POT','SKILLET','WOK','SHEET_PAN','CASSEROLE','SLOW_COOKER','GRILL','PASTA','RICE_BOWL','SALAD','TACO','WRAP','SANDWICH','PIZZA','PANCAKES','EGG','TOAST','PORRIDGE','PARFAIT','SMOOTHIE','MUFFIN','BREAD','COOKIE','PIE','CAKE'));

ALTER TABLE recipe_presets ADD COLUMN icon VARCHAR(20);
ALTER TABLE recipe_presets ADD CONSTRAINT ck_recipe_presets_icon
    CHECK (icon IS NULL OR icon IN ('POT','SKILLET','WOK','SHEET_PAN','CASSEROLE','SLOW_COOKER','GRILL','PASTA','RICE_BOWL','SALAD','TACO','WRAP','SANDWICH','PIZZA','PANCAKES','EGG','TOAST','PORRIDGE','PARFAIT','SMOOTHIE','MUFFIN','BREAD','COOKIE','PIE','CAKE'));

-- Every starter recipe gets a picture, so imports arrive with one.
UPDATE recipe_presets p
SET icon = v.icon
FROM (VALUES
    ('Baked Mac and Cheese', 'CASSEROLE'),
    ('Beef Taco Bowls', 'RICE_BOWL'),
    ('Beef and Potato Hash', 'SKILLET'),
    ('Black Bean Chili', 'POT'),
    ('Black Bean Rice Bowls', 'RICE_BOWL'),
    ('Black Bean Tacos', 'TACO'),
    ('Breakfast Burritos', 'WRAP'),
    ('Breakfast Quesadilla', 'SKILLET'),
    ('Brown Sugar Shortbread', 'COOKIE'),
    ('Cheesy Bean Enchilada Skillet', 'SKILLET'),
    ('Cheesy Scrambled Eggs', 'EGG'),
    ('Chicken Fried Rice', 'WOK'),
    ('Chicken Quesadillas', 'SKILLET'),
    ('Chicken Rice Soup', 'POT'),
    ('Chicken Salad Wraps', 'WRAP'),
    ('Chicken Tortilla Soup', 'POT'),
    ('Chicken and Peas', 'SKILLET'),
    ('Chicken and Rice Skillet', 'SKILLET'),
    ('Chocolate Chip Cookies', 'COOKIE'),
    ('Chocolate Oatmeal', 'PORRIDGE'),
    ('Chocolate Peanut Butter Bites', 'COOKIE'),
    ('Cinnamon French Toast', 'PANCAKES'),
    ('Cinnamon Oatmeal', 'PORRIDGE'),
    ('Cinnamon Sugar Cookies', 'COOKIE'),
    ('Cinnamon Sugar Toast', 'TOAST'),
    ('Classic Pancakes', 'PANCAKES'),
    ('Creamy Chicken Pasta', 'PASTA'),
    ('Creamy Tomato Pasta', 'PASTA'),
    ('French Toast Sticks', 'PANCAKES'),
    ('Garlic Butter Chicken', 'SKILLET'),
    ('Garlic Parmesan Pasta', 'PASTA'),
    ('Greek Lemon Potatoes', 'SHEET_PAN'),
    ('Greek Yogurt Parfait', 'PARFAIT'),
    ('Grilled Cheese and Tomato Soup', 'SANDWICH'),
    ('Honey Oat Cookies', 'COOKIE'),
    ('Honey Yogurt Dip', 'PARFAIT'),
    ('Italian Sausage Pasta', 'PASTA'),
    ('Lemon Chicken Rice Bowls', 'RICE_BOWL'),
    ('Lentil Soup', 'POT'),
    ('Lentil Tomato Salad', 'SALAD'),
    ('Mediterranean Couscous Salad', 'SALAD'),
    ('Overnight Oats', 'PORRIDGE'),
    ('Peanut Butter Oat Bites', 'COOKIE'),
    ('Peanut Butter Toast', 'TOAST'),
    ('Savory Breakfast Rice', 'RICE_BOWL'),
    ('Spaghetti Marinara', 'PASTA'),
    ('Spinach Egg Muffins', 'MUFFIN'),
    ('Spinach Quesadillas', 'SKILLET'),
    ('Tomato Basil Pasta', 'PASTA'),
    ('Tomato Lentil Pasta', 'PASTA'),
    ('Vegetable Fried Rice', 'WOK'),
    ('Veggie Omelet', 'EGG')
) AS v (name, icon)
WHERE p.name = v.name;

-- Starter recipes already imported into accounts get the same picture, as
-- long as the cook has not renamed them. Anyone can change or clear it.
UPDATE recipes r
SET icon = p.icon
FROM recipe_presets p
WHERE r.icon IS NULL
  AND p.icon IS NOT NULL
  AND LOWER(r.name) = LOWER(p.name);
