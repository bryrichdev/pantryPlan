-- Meal type and cuisine were free to drift. Brunch and Dessert match no meal
-- plan slot, so auto-fill never scheduled those recipes, and cuisine collected
-- one-off values. This folds everything onto a fixed list and adds CHECK
-- constraints so the database refuses anything outside it from now on.
--
-- The lists here must match RecipeClassification in the Java code.

-- ---------------------------------------------------------------- meal type

UPDATE recipes SET meal_type = 'Breakfast' WHERE LOWER(TRIM(meal_type)) = 'brunch';
UPDATE recipes SET meal_type = 'Snack'     WHERE LOWER(TRIM(meal_type)) = 'dessert';
UPDATE recipes SET meal_type = INITCAP(LOWER(TRIM(meal_type)))
    WHERE LOWER(TRIM(meal_type)) IN ('breakfast', 'lunch', 'dinner', 'snack');
UPDATE recipes SET meal_type = NULL
    WHERE meal_type IS NOT NULL AND meal_type NOT IN ('Breakfast', 'Lunch', 'Dinner', 'Snack');

UPDATE recipe_presets SET meal_type = 'Breakfast' WHERE LOWER(TRIM(meal_type)) = 'brunch';
UPDATE recipe_presets SET meal_type = 'Snack'     WHERE LOWER(TRIM(meal_type)) = 'dessert';
UPDATE recipe_presets SET meal_type = INITCAP(LOWER(TRIM(meal_type)))
    WHERE LOWER(TRIM(meal_type)) IN ('breakfast', 'lunch', 'dinner', 'snack');

ALTER TABLE recipes ADD CONSTRAINT ck_recipes_meal_type
    CHECK (meal_type IS NULL OR meal_type IN ('Breakfast', 'Lunch', 'Dinner', 'Snack'));
ALTER TABLE recipe_presets ADD CONSTRAINT ck_recipe_presets_meal_type
    CHECK (meal_type IN ('Breakfast', 'Lunch', 'Dinner', 'Snack'));

-- ------------------------------------------------------------------ cuisine

CREATE TEMPORARY TABLE cuisine_names (name VARCHAR(60) PRIMARY KEY) ON COMMIT DROP;
INSERT INTO cuisine_names (name) VALUES
    ('American'), ('Asian'), ('British'), ('Chinese'), ('French'), ('Greek'),
    ('Indian'), ('Italian'), ('Japanese'), ('Korean'), ('Mediterranean'),
    ('Mexican'), ('Middle Eastern'), ('Thai'), ('Vietnamese'), ('Other');

UPDATE recipes SET nationality = 'Asian'
    WHERE LOWER(TRIM(nationality)) IN ('asian-inspired', 'asian inspired');
UPDATE recipes SET nationality = 'British'
    WHERE LOWER(TRIM(nationality)) IN ('scottish', 'english', 'irish', 'welsh');
UPDATE recipes r SET nationality = c.name
    FROM cuisine_names c
    WHERE LOWER(TRIM(r.nationality)) = LOWER(c.name);
UPDATE recipes SET nationality = 'Other'
    WHERE nationality IS NOT NULL AND nationality NOT IN (SELECT name FROM cuisine_names);

UPDATE recipe_presets SET nationality = 'Asian'
    WHERE LOWER(TRIM(nationality)) IN ('asian-inspired', 'asian inspired');
UPDATE recipe_presets SET nationality = 'British'
    WHERE LOWER(TRIM(nationality)) IN ('scottish', 'english', 'irish', 'welsh');
UPDATE recipe_presets p SET nationality = c.name
    FROM cuisine_names c
    WHERE LOWER(TRIM(p.nationality)) = LOWER(c.name);
UPDATE recipe_presets SET nationality = 'Other'
    WHERE nationality NOT IN (SELECT name FROM cuisine_names);

ALTER TABLE recipes ADD CONSTRAINT ck_recipes_nationality
    CHECK (nationality IS NULL OR nationality IN (
        'American', 'Asian', 'British', 'Chinese', 'French', 'Greek',
        'Indian', 'Italian', 'Japanese', 'Korean', 'Mediterranean',
        'Mexican', 'Middle Eastern', 'Thai', 'Vietnamese', 'Other'));
ALTER TABLE recipe_presets ADD CONSTRAINT ck_recipe_presets_nationality
    CHECK (nationality IN (
        'American', 'Asian', 'British', 'Chinese', 'French', 'Greek',
        'Indian', 'Italian', 'Japanese', 'Korean', 'Mediterranean',
        'Mexican', 'Middle Eastern', 'Thai', 'Vietnamese', 'Other'));
