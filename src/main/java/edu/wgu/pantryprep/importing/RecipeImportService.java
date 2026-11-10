package edu.wgu.pantryprep.importing;

import static edu.wgu.pantryprep.importing.ImportWorkbooks.AMOUNT;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.CATEGORY;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.COOK;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.CUISINE;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.DESCRIPTION;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.INGREDIENT;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.INSTRUCTIONS;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.MEAL_TYPE;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.NAME;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.NOTE;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.PICTURE;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.PREP;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.RECIPE;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.RECIPES;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.RECIPE_INGREDIENTS;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.RECIPE_WORKBOOK;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.SERVINGS;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.TAGS;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.UNIT;

import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.IngredientCategory;
import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.RecipeIcon;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.importing.ImportReport.SheetCount;
import edu.wgu.pantryprep.importing.WorkbookSpec.Sheet;
import edu.wgu.pantryprep.repository.IngredientRepository;
import edu.wgu.pantryprep.repository.RecipeRepository;
import edu.wgu.pantryprep.service.RecipeService;
import edu.wgu.pantryprep.web.form.RecipeForm;
import edu.wgu.pantryprep.web.form.RecipeLineForm;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a filled-in recipes spreadsheet into recipes. A preview reads and
 * checks everything and saves nothing; an import does the same checks again
 * and then saves all of it in one transaction, or nothing when anything is
 * wrong. Ingredient names that match none of the cook's ingredients become new
 * ingredients.
 */
@Service
public class RecipeImportService {

    private final RecipeService recipeService;
    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;

    public RecipeImportService(RecipeService recipeService, RecipeRepository recipeRepository,
                               IngredientRepository ingredientRepository) {
        this.recipeService = recipeService;
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
    }

    public byte[] template() {
        return RECIPE_WORKBOOK.template();
    }

    @Transactional(readOnly = true)
    public ImportReport preview(byte[] file, User user) {
        Plan plan = plan(file, user);
        return plan.report(false);
    }

    @Transactional
    public ImportReport importFile(byte[] file, User user) {
        Plan plan = plan(file, user);
        if (!plan.problems.isEmpty()) {
            return plan.report(false);
        }
        plan.matcher.saveNew(ingredientRepository);
        for (PlannedRecipe recipe : plan.recipes.values()) {
            RecipeForm form = recipe.form;
            for (int i = 0; i < recipe.ingredients.size(); i++) {
                form.getLines().get(i).setIngredientId(recipe.ingredients.get(i).getId());
            }
            recipeService.create(form, user);
        }
        return plan.report(true);
    }

    // ------------------------------------------------------------ planning

    private static final class PlannedRecipe {
        final RecipeForm form = new RecipeForm();
        final List<Ingredient> ingredients = new ArrayList<>();
    }

    private static final class Plan {
        final Problems problems = new Problems(RECIPE_WORKBOOK);
        final Map<String, PlannedRecipe> recipes = new LinkedHashMap<>();
        IngredientMatcher matcher;
        int lineCount;

        ImportReport report(boolean saved) {
            if (!problems.isEmpty()) {
                return ImportReport.failed(problems.sorted());
            }
            return new ImportReport(saved, List.of(),
                    List.of(new SheetCount(RECIPES.name(), recipes.size()),
                            new SheetCount(RECIPE_INGREDIENTS.name(), lineCount)),
                    recipes.values().stream().map(r -> r.form.getName()).toList(),
                    matcher.plannedNames());
        }
    }

    private Plan plan(byte[] file, User user) {
        Plan plan = new Plan();
        Map<Sheet, List<ParsedRow>> rows = WorkbookFile.read(RECIPE_WORKBOOK, file, plan.problems);
        if (rows == null) {
            return plan;
        }
        List<ParsedRow> recipeRows = rows.get(RECIPES);
        List<ParsedRow> lineRows = rows.get(RECIPE_INGREDIENTS);
        if (recipeRows.isEmpty() && lineRows.isEmpty()) {
            plan.problems.file("There's nothing to import yet. Add your recipes to the Recipes sheet, one per "
                    + "row, below the headers.");
            return plan;
        }
        plan.matcher = new IngredientMatcher(user, ingredientRepository.findAllByUserOrderByNameAsc(user));

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
            plan.lineCount++;
        }
        return plan;
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
        return recipe;
    }
}
