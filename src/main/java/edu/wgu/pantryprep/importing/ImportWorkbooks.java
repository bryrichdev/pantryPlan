package edu.wgu.pantryprep.importing;

import static edu.wgu.pantryprep.importing.WorkbookSpec.optional;
import static edu.wgu.pantryprep.importing.WorkbookSpec.required;

import edu.wgu.pantryprep.domain.IngredientCategory;
import edu.wgu.pantryprep.domain.RecipeClassification;
import edu.wgu.pantryprep.domain.RecipeIcon;
import edu.wgu.pantryprep.domain.StorageLocation;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.importing.WorkbookSpec.Column;
import edu.wgu.pantryprep.importing.WorkbookSpec.Sheet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The spreadsheet a cook can download, fill in and import: recipes with their
 * ingredients, what is in the pantry, and ingredients themselves, each on its
 * own sheet. Limits match the forms, so a row that imports is one the form
 * would have accepted.
 */
public final class ImportWorkbooks {

    // Column keys.
    public static final String NAME = "name";
    public static final String SERVINGS = "servings";
    public static final String PREP = "prepMinutes";
    public static final String COOK = "cookMinutes";
    public static final String MEAL_TYPE = "mealType";
    public static final String CUISINE = "cuisine";
    public static final String PICTURE = "picture";
    public static final String TAGS = "tags";
    public static final String DESCRIPTION = "description";
    public static final String INSTRUCTIONS = "instructions";
    public static final String SOURCE = "sourceUrl";
    public static final String RECIPE = "recipe";
    public static final String INGREDIENT = "ingredient";
    public static final String AMOUNT = "amount";
    public static final String UNIT = "unit";
    public static final String NOTE = "note";
    public static final String CATEGORY = "category";
    public static final String LOCATION = "location";
    public static final String PURCHASED = "purchasedOn";
    public static final String EXPIRES = "expiresOn";
    public static final String USUAL_AMOUNT = "defaultQuantity";
    public static final String GRAMS_PER_CUP = "gramsPerCup";

    private ImportWorkbooks() {
    }

    // ------------------------------------------------------- shared columns

    /** Units by their short name, plus the spellings people write out. */
    static final ValueType UNIT_TYPE = ValueType.choice(Unit.class, Unit::getAbbreviation, unitAliases());

    private static Map<String, Unit> unitAliases() {
        Map<String, Unit> aliases = new LinkedHashMap<>();
        for (String alias : List.of("gram", "grams", "gr")) aliases.put(alias, Unit.GRAM);
        for (String alias : List.of("kilogram", "kilograms", "kilo", "kilos", "kgs")) aliases.put(alias, Unit.KILOGRAM);
        for (String alias : List.of("ounce", "ounces")) aliases.put(alias, Unit.OUNCE);
        for (String alias : List.of("pound", "pounds", "lbs")) aliases.put(alias, Unit.POUND);
        for (String alias : List.of("milliliter", "milliliters", "millilitre", "millilitres", "mls")) {
            aliases.put(alias, Unit.MILLILITER);
        }
        for (String alias : List.of("liter", "liters", "litre", "litres")) aliases.put(alias, Unit.LITER);
        for (String alias : List.of("teaspoon", "teaspoons", "tsps")) aliases.put(alias, Unit.TEASPOON);
        for (String alias : List.of("tablespoon", "tablespoons", "tbsps", "tbs", "tbl")) {
            aliases.put(alias, Unit.TABLESPOON);
        }
        for (String alias : List.of("cups")) aliases.put(alias, Unit.CUP);
        for (String alias : List.of("piece", "pieces", "pcs", "each", "ea", "whole", "count")) {
            aliases.put(alias, Unit.PIECE);
        }
        return aliases;
    }

    static final ValueType CATEGORY_TYPE = ValueType.choice(IngredientCategory.class);

    private static Column ingredient(String note) {
        return required(INGREDIENT, "Ingredient", ValueType.text(120), 24, note);
    }

    private static Column category() {
        return optional(CATEGORY, "Category", CATEGORY_TYPE, 15,
                "Only used for an ingredient you don't have yet. Other if left blank");
    }

    // -------------------------------------------------------------- recipes

    public static final Sheet RECIPES = new Sheet("Recipes",
            "One row per recipe. Each recipe's ingredients go on the Recipe Ingredients sheet.",
            List.of(
                    required(NAME, "Name", ValueType.text(150), 30,
                            "Each name can be used once, and not for a recipe you already have"),
                    required(SERVINGS, "Serves", ValueType.whole(1, 100), 9, "How many it feeds"),
                    optional(PREP, "Prep minutes", ValueType.whole(0, 1440), 12, null),
                    optional(COOK, "Cook minutes", ValueType.whole(0, 1440), 12, null),
                    optional(MEAL_TYPE, "Meal type", ValueType.oneOf(RecipeClassification.MEAL_TYPES), 12,
                            "Auto-fill puts a recipe only in slots of its meal type"),
                    optional(CUISINE, "Cuisine", ValueType.oneOf(RecipeClassification.CUISINES), 15, null),
                    optional(PICTURE, "Picture", ValueType.choice(RecipeIcon.class, RecipeIcon::getLabel, Map.of()),
                            17, "A little drawing to spot it by in lists"),
                    optional(TAGS, "Tags", ValueType.text(300), 22, "Separate with commas, such as weeknight, freezer"),
                    optional(DESCRIPTION, "Description", ValueType.text(2000), 34, "One or two lines about the dish"),
                    optional(INSTRUCTIONS, "Instructions", ValueType.text(20000), 50, "The method, in as many lines as you like"),
                    optional(SOURCE, "Source link", ValueType.text(500), 30,
                            "Where the recipe came from, such as https://example.com/recipe")));

    public static final Sheet RECIPE_INGREDIENTS = new Sheet("Recipe Ingredients",
            "One row per ingredient in a recipe: a recipe with six ingredients has six rows here.",
            List.of(
                    required(RECIPE, "Recipe", ValueType.text(150), 30,
                            "The recipe's name, as written on the Recipes sheet"),
                    ingredient("Matched to your ingredients by name. One you don't have yet is added for you, "
                            + "kept in the unit used here"),
                    required(AMOUNT, "Amount", ValueType.amount(false), 10, null),
                    required(UNIT, "Unit", UNIT_TYPE, 8, null),
                    optional(NOTE, "Note", ValueType.text(255), 24, "Such as diced, or to taste"),
                    category()));

    // --------------------------------------------------------------- pantry

    public static final Sheet PANTRY = new Sheet("Pantry",
            "One row per thing on your shelves. Two bags of flour in different places can be two rows.",
            List.of(
                    ingredient("Matched to your ingredients by name. One you don't have yet is added for you"),
                    required(AMOUNT, "Amount", ValueType.amount(true), 10, null),
                    optional(UNIT, "Unit", UNIT_TYPE, 8,
                            "Leave blank to use the unit the ingredient is kept in. Another unit is converted "
                                    + "to that one. Required for an ingredient you don't have yet"),
                    optional(LOCATION, "Location", ValueType.choice(StorageLocation.class), 12,
                            "Where the ingredient usually lives if left blank"),
                    optional(PURCHASED, "Purchased", ValueType.DATE, 12, "Today if left blank"),
                    optional(EXPIRES, "Expires", ValueType.DATE, 12, "Leave blank if it keeps"),
                    category()));

    // ---------------------------------------------------------- ingredients

    public static final Sheet INGREDIENTS = new Sheet("Ingredients",
            "Optional. One row per ingredient you want set up with its details. Any ingredient the other "
                    + "sheets use that isn't here or on your list is still added for you.",
            List.of(
                    required(NAME, "Name", ValueType.text(120), 26,
                            "One you already have is left as it is"),
                    optional(CATEGORY, "Category", CATEGORY_TYPE, 15, "Other if left blank"),
                    required(UNIT, "Kept in", UNIT_TYPE, 9,
                            "The unit your pantry counts it in, such as g for flour or pc for eggs"),
                    optional(LOCATION, "Usually stored", ValueType.choice(StorageLocation.class), 14,
                            "Picked from the category if left blank"),
                    optional(USUAL_AMOUNT, "Usual amount", ValueType.amount(false), 12,
                            "How much you usually buy, in the Kept in unit"),
                    optional(GRAMS_PER_CUP, "Grams per cup", ValueType.amount(false), 13,
                            "Lets recipes in cups use stock kept in grams, such as 120 for flour")));

    // ------------------------------------------------------------- workbook

    /** The one spreadsheet: fill in any of its sheets and leave the rest empty. */
    public static final WorkbookSpec WORKBOOK = new WorkbookSpec(
            "PantryPrep import",
            "pantryprep-import-template.xlsx",
            List.of(
                    "Fill in whichever sheets you need and leave the others empty. Recipes, pantry items and "
                            + "ingredients can all come in from one file.",
                    "Recipes: one recipe per row on the Recipes sheet. List each recipe's ingredients on the "
                            + "Recipe Ingredients sheet, one per row, naming the recipe they belong to.",
                    "Pantry: one row per thing on your shelves. Importing adds to your pantry; it doesn't "
                            + "replace what's there.",
                    "Ingredients: optional. Use it to set up ingredients with their unit, storage place and "
                            + "weight per cup. Ingredients the other sheets use are matched to yours by name, "
                            + "ignoring case, and any that don't match are added for you.",
                    "Keep row 1 on each sheet as it is: PantryPrep finds every column by its header. Column "
                            + "order doesn't matter. Green headers marked * are required on every row you fill in.",
                    "Where a column has a dropdown, pick from it. Amounts can be 2, 1.5, 1/2 or 1 1/2. Dates "
                            + "can be typed as 2026-01-31 or 1/31/2026.",
                    "To import: in PantryPrep, choose Import spreadsheet on the Recipes, Pantry or Ingredients "
                            + "page, upload this file and check the preview. Nothing is saved until you click "
                            + "Import, and then everything is saved together or not at all."),
            List.of(RECIPES, RECIPE_INGREDIENTS, PANTRY, INGREDIENTS));
}
