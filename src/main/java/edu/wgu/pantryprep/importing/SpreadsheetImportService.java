package edu.wgu.pantryprep.importing;

import static edu.wgu.pantryprep.importing.ImportWorkbooks.AMOUNT;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.CATEGORY;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.COOK;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.CUISINE;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.DESCRIPTION;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.EXPIRES;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.GRAMS_PER_CUP;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.INGREDIENT;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.INGREDIENTS;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.INSTRUCTIONS;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.LOCATION;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.MEAL_TYPE;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.NAME;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.NOTE;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.PANTRY;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.PICTURE;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.PREP;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.PURCHASED;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.RECIPE;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.RECIPES;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.RECIPE_INGREDIENTS;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.SERVINGS;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.SOURCE;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.TAGS;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.UNIT;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.USUAL_AMOUNT;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.WORKBOOK;

import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.IngredientCategory;
import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.RecipeIcon;
import edu.wgu.pantryprep.domain.SourceLinks;
import edu.wgu.pantryprep.domain.StorageLocation;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.importing.ImportReport.SheetCount;
import edu.wgu.pantryprep.importing.WorkbookSpec.Sheet;
import edu.wgu.pantryprep.repository.IngredientRepository;
import edu.wgu.pantryprep.repository.RecipeRepository;
import edu.wgu.pantryprep.service.PantryService;
import edu.wgu.pantryprep.service.RecipeService;
import edu.wgu.pantryprep.service.UnitConversionService;
import edu.wgu.pantryprep.web.form.PantryItemForm;
import edu.wgu.pantryprep.web.form.RecipeForm;
import edu.wgu.pantryprep.web.form.RecipeLineForm;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns the filled-in import spreadsheet into ingredients, recipes and
 * pantry items. A preview reads and checks everything and saves nothing. An
 * import does the same checks again and then saves all of it in one
 * transaction, or nothing when anything is wrong.
 *
 * <p>The sheets share one list of ingredients. The Ingredients sheet is read
 * first, so a recipe or pantry row naming an ingredient set up there gets its
 * details. Any other name that matches none of the cook's ingredients becomes
 * a new ingredient, taking its category and unit from the first row that
 * names it.
 */
@Service
public class SpreadsheetImportService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999.999");

    private final RecipeService recipeService;
    private final RecipeRepository recipeRepository;
    private final PantryService pantryService;
    private final IngredientRepository ingredientRepository;
    private final UnitConversionService conversions;
    private final Clock clock;

    @Autowired
    public SpreadsheetImportService(RecipeService recipeService, RecipeRepository recipeRepository,
                                    PantryService pantryService, IngredientRepository ingredientRepository,
                                    UnitConversionService conversions) {
        this(recipeService, recipeRepository, pantryService, ingredientRepository, conversions,
                Clock.systemDefaultZone());
    }

    SpreadsheetImportService(RecipeService recipeService, RecipeRepository recipeRepository,
                             PantryService pantryService, IngredientRepository ingredientRepository,
                             UnitConversionService conversions, Clock clock) {
        this.recipeService = recipeService;
        this.recipeRepository = recipeRepository;
        this.pantryService = pantryService;
        this.ingredientRepository = ingredientRepository;
        this.conversions = conversions;
        this.clock = clock;
    }

    public byte[] template() {
        return WORKBOOK.template();
    }

    @Transactional(readOnly = true)
    public ImportReport preview(byte[] file, User user) {
        return plan(file, user).report(false);
    }

    @Transactional
    public ImportReport importFile(byte[] file, User user) {
        Plan plan = plan(file, user);
        if (!plan.problems.isEmpty()) {
            return plan.report(false);
        }
        plan.matcher.saveNew(ingredientRepository);
        for (PlannedRecipe recipe : plan.recipes.values()) {
            for (int i = 0; i < recipe.ingredients.size(); i++) {
                recipe.form.getLines().get(i).setIngredientId(recipe.ingredients.get(i).getId());
            }
            recipeService.create(recipe.form, user);
        }
        for (PlannedItem item : plan.items) {
            item.form.setIngredientId(item.ingredient.getId());
            pantryService.create(item.form, user);
        }
        return plan.report(true);
    }

    // ------------------------------------------------------------ planning

    private static final class PlannedRecipe {
        final RecipeForm form = new RecipeForm();
        final List<Ingredient> ingredients = new ArrayList<>();
    }

    private record PlannedItem(PantryItemForm form, Ingredient ingredient) {
    }

    private static final class Plan {
        final Problems problems = new Problems(WORKBOOK);
        final Map<String, PlannedRecipe> recipes = new LinkedHashMap<>();
        final List<PlannedItem> items = new ArrayList<>();
        final List<String> unchanged = new ArrayList<>();
        final List<SheetCount> counts = new ArrayList<>();
        IngredientMatcher matcher;

        ImportReport report(boolean saved) {
            if (!problems.isEmpty()) {
                return ImportReport.failed(problems.sorted());
            }
            Set<String> stocked = new LinkedHashSet<>();
            items.forEach(item -> stocked.add(item.ingredient().getName()));
            return new ImportReport(saved, List.of(), counts,
                    recipes.values().stream().map(r -> r.form.getName()).toList(),
                    items.size(), List.copyOf(stocked), matcher.plannedNames(), unchanged);
        }
    }

    private Plan plan(byte[] file, User user) {
        Plan plan = new Plan();
        Map<Sheet, List<ParsedRow>> rows = WorkbookFile.read(WORKBOOK, file, plan.problems);
        if (rows == null) {
            return plan;
        }
        if (rows.values().stream().allMatch(List::isEmpty)) {
            plan.problems.file("There's nothing to import yet. Fill in the Recipes, Pantry or Ingredients sheet "
                    + "below its headers, then upload the file again.");
            return plan;
        }
        for (Sheet sheet : WORKBOOK.sheets()) {
            if (!rows.get(sheet).isEmpty()) {
                plan.counts.add(new SheetCount(sheet.name(), rows.get(sheet).size()));
            }
        }
        plan.matcher = new IngredientMatcher(user, ingredientRepository.findAllByUserOrderByNameAsc(user));
        planIngredients(plan, rows.get(INGREDIENTS));
        planRecipes(plan, rows.get(RECIPES), rows.get(RECIPE_INGREDIENTS), user);
        planPantry(plan, rows.get(PANTRY));
        return plan;
    }

    // --------------------------------------------------------- ingredients

    private void planIngredients(Plan plan, List<ParsedRow> rows) {
        Map<String, Integer> firstRow = new LinkedHashMap<>();
        for (ParsedRow row : rows) {
            String name = row.text(NAME);
            Unit unit = row.get(UNIT);
            if (name == null || unit == null) {
                continue;
            }
            String key = IngredientMatcher.key(name);
            if (firstRow.containsKey(key)) {
                plan.problems.at(row, NAME, "\"" + name + "\" is already on row " + firstRow.get(key)
                        + ". List each ingredient once.");
                continue;
            }
            firstRow.put(key, row.number());
            Ingredient existing = plan.matcher.find(name);
            if (existing != null) {
                plan.unchanged.add(existing.getName());
                continue;
            }
            IngredientCategory category = row.get(CATEGORY);
            StorageLocation location = row.get(LOCATION);
            plan.matcher.planDetailed(name, category, unit, location, row.get(USUAL_AMOUNT), row.get(GRAMS_PER_CUP));
        }
    }

    // ------------------------------------------------------------- recipes

    private void planRecipes(Plan plan, List<ParsedRow> recipeRows, List<ParsedRow> lineRows, User user) {
        Set<String> taken = new HashSet<>();
        for (Recipe existing : recipeRepository.findAllByUserOrderByNameAsc(user)) {
            taken.add(IngredientMatcher.key(existing.getName()));
        }
        Map<String, Integer> firstRow = new LinkedHashMap<>();
        for (ParsedRow row : recipeRows) {
            String name = row.text(NAME);
            if (name == null) {
                continue;
            }
            String key = IngredientMatcher.key(name);
            if (firstRow.containsKey(key)) {
                plan.problems.at(row, NAME, "\"" + name + "\" is already on row " + firstRow.get(key)
                        + ". Give each recipe its own name.");
                continue;
            }
            firstRow.put(key, row.number());
            if (taken.contains(key)) {
                plan.problems.at(row, NAME, "You already have a recipe called \"" + name
                        + "\". Rename this one, or leave it out of the file.");
                continue;
            }
            String source = row.text(SOURCE);
            if (!SourceLinks.isAcceptable(source)) {
                plan.problems.at(row, SOURCE, "\"" + source + "\" isn't a web address. Use one such as "
                        + "https://example.com/recipe, or leave it blank.");
                continue;
            }
            plan.recipes.put(key, toRecipe(row, name));
        }

        for (ParsedRow row : lineRows) {
            String recipeName = row.text(RECIPE);
            String ingredientName = row.text(INGREDIENT);
            BigDecimal amount = row.get(AMOUNT);
            Unit unit = row.get(UNIT);
            if (recipeName == null || ingredientName == null || amount == null || unit == null) {
                continue;
            }
            String key = IngredientMatcher.key(recipeName);
            PlannedRecipe recipe = plan.recipes.get(key);
            if (recipe == null) {
                if (!firstRow.containsKey(key)) {
                    plan.problems.at(row, RECIPE, "There's no recipe called \"" + recipeName
                            + "\" on the Recipes sheet. Check the spelling, or add a row for it there.");
                }
                // A recipe with its own problem: its lines are left alone rather than reported twice.
                continue;
            }
            Ingredient ingredient = plan.matcher.find(ingredientName);
            if (ingredient == null) {
                IngredientCategory category = row.get(CATEGORY);
                ingredient = plan.matcher.plan(ingredientName, category, unit);
            }
            RecipeLineForm line = new RecipeLineForm();
            line.setQuantity(amount);
            line.setUnit(unit);
            line.setNote(row.text(NOTE));
            recipe.form.getLines().add(line);
            recipe.ingredients.add(ingredient);
        }
    }

    private static PlannedRecipe toRecipe(ParsedRow row, String name) {
        PlannedRecipe recipe = new PlannedRecipe();
        RecipeForm form = recipe.form;
        form.setName(IngredientMatcher.tidy(name));
        if (row.has(SERVINGS)) {
            form.setServings(row.<Integer>get(SERVINGS));
        }
        form.setPrepMinutes(row.has(PREP) ? row.<Integer>get(PREP) : 0);
        form.setCookMinutes(row.has(COOK) ? row.<Integer>get(COOK) : 0);
        form.setMealType(row.text(MEAL_TYPE));
        form.setNationality(row.text(CUISINE));
        form.setIcon(row.<RecipeIcon>get(PICTURE));
        form.setTagsCsv(row.text(TAGS));
        form.setDescription(row.text(DESCRIPTION));
        form.setInstructions(row.text(INSTRUCTIONS));
        form.setSourceUrl(row.text(SOURCE));
        return recipe;
    }

    // -------------------------------------------------------------- pantry

    private void planPantry(Plan plan, List<ParsedRow> rows) {
        LocalDate today = LocalDate.now(clock);
        for (ParsedRow row : rows) {
            String name = row.text(INGREDIENT);
            BigDecimal amount = row.get(AMOUNT);
            Unit unit = row.get(UNIT);
            if (name == null || amount == null) {
                continue;
            }
            Ingredient ingredient = plan.matcher.find(name);
            if (ingredient == null) {
                if (unit == null) {
                    plan.problems.at(row, UNIT, "\"" + IngredientMatcher.tidy(name) + "\" isn't one of your "
                            + "ingredients yet, so give a unit for it to be kept in, or add it to the "
                            + "Ingredients sheet");
                    continue;
                }
                IngredientCategory category = row.get(CATEGORY);
                ingredient = plan.matcher.plan(name, category, unit);
            }

            BigDecimal quantity = amount;
            if (unit != null && unit != ingredient.getStockUnit()) {
                Optional<BigDecimal> converted = conversions.convert(amount, unit, ingredient.getStockUnit(),
                        ingredient);
                if (converted.isEmpty()) {
                    plan.problems.at(row, UNIT, sentence(conversions.explainFailure(unit,
                            ingredient.getStockUnit(), ingredient)) + ". Give the amount in "
                            + ingredient.getStockUnit().getAbbreviation() + ", or leave Unit blank.");
                    continue;
                }
                quantity = converted.get().setScale(3, RoundingMode.HALF_UP);
                if (quantity.compareTo(MAX_AMOUNT) > 0) {
                    plan.problems.at(row, AMOUNT, "That comes to more than " + MAX_AMOUNT.toPlainString() + " "
                            + ingredient.getStockUnit().getAbbreviation() + ", which is more than a pantry row holds");
                    continue;
                }
            }

            LocalDate purchased = row.has(PURCHASED) ? row.get(PURCHASED) : today;
            LocalDate expires = row.get(EXPIRES);
            if (expires != null && expires.isBefore(purchased)) {
                plan.problems.at(row, EXPIRES, "The expiry date falls before the purchase date");
                continue;
            }
            StorageLocation location = row.has(LOCATION) ? row.get(LOCATION) : ingredient.getDefaultLocation();

            PantryItemForm form = new PantryItemForm();
            form.setQuantity(quantity);
            form.setLocation(location);
            form.setPurchasedOn(purchased);
            form.setExpiresOn(expires);
            plan.items.add(new PlannedItem(form, ingredient));
        }
    }

    private static String sentence(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
