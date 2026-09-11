package edu.wgu.pantryplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.IngredientCategory;
import edu.wgu.pantryplan.domain.MealPlan;
import edu.wgu.pantryplan.domain.MealSlot;
import edu.wgu.pantryplan.domain.PantryItem;
import edu.wgu.pantryplan.domain.Recipe;
import edu.wgu.pantryplan.domain.StorageLocation;
import edu.wgu.pantryplan.domain.Unit;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.service.BulkDeleteResult;
import edu.wgu.pantryplan.service.IngredientService;
import edu.wgu.pantryplan.service.MealPlanService;
import edu.wgu.pantryplan.service.PantryService;
import edu.wgu.pantryplan.service.RecipeService;
import edu.wgu.pantryplan.service.UserService;
import edu.wgu.pantryplan.web.form.IngredientForm;
import edu.wgu.pantryplan.web.form.MealPlanForm;
import edu.wgu.pantryplan.web.form.PantryItemForm;
import edu.wgu.pantryplan.web.form.PlanEntryForm;
import edu.wgu.pantryplan.web.form.RecipeForm;
import edu.wgu.pantryplan.web.form.RecipeLineForm;
import edu.wgu.pantryplan.web.form.RegistrationForm;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class BulkDeleteTests {

    @Autowired
    private IngredientService ingredientService;

    @Autowired
    private PantryService pantryService;

    @Autowired
    private MealPlanService mealPlanService;

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private UserService userService;

    private User cook(String email) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName("Cook");
        form.setEmail(email);
        form.setPassword("correcthorsebattery");
        form.setConfirmPassword("correcthorsebattery");
        return userService.register(form);
    }

    private Ingredient ingredient(User user, String name) {
        IngredientForm form = new IngredientForm();
        form.setName(name);
        form.setCategory(IngredientCategory.OTHER);
        form.setStockUnit(Unit.GRAM);
        form.setDefaultLocation(StorageLocation.PANTRY);
        return ingredientService.create(form, user);
    }

    private PantryItem stock(User user, Ingredient ingredient, String grams) {
        PantryItemForm form = new PantryItemForm();
        form.setIngredientId(ingredient.getId());
        form.setQuantity(new BigDecimal(grams));
        form.setLocation(StorageLocation.PANTRY);
        return pantryService.create(form, user);
    }

    private void recipeUsing(User user, String name, Ingredient ingredient) {
        RecipeForm form = new RecipeForm();
        form.setName(name);
        form.setServings(4);
        RecipeLineForm line = new RecipeLineForm();
        line.setIngredientId(ingredient.getId());
        line.setQuantity(new BigDecimal("100"));
        line.setUnit(Unit.GRAM);
        form.getLines().add(line);
        recipeService.create(form, user);
    }

    private Recipe recipe(User user, String name) {
        RecipeForm form = new RecipeForm();
        form.setName(name);
        form.setServings(4);
        return recipeService.create(form, user);
    }

    @Test
    void deletesEverySelectedIngredient() {
        User user = cook("bulk-ing@example.com");
        Ingredient one = ingredient(user, "Alpha");
        Ingredient two = ingredient(user, "Beta");
        ingredient(user, "Gamma");

        BulkDeleteResult result = ingredientService.deleteAll(
                List.of(one.getId(), two.getId()), user);

        assertEquals(2, result.getDeletedCount());
        assertFalse(result.hasBlocked());
        assertEquals(1, ingredientService.findAll(user).size());
    }

    @Test
    void keepsBackReferencedIngredientsAndDeletesTheRest() {
        User user = cook("bulk-partial@example.com");
        Ingredient free = ingredient(user, "Unused");
        Ingredient used = ingredient(user, "Oats");
        recipeUsing(user, "Porridge", used);

        BulkDeleteResult result = ingredientService.deleteAll(
                List.of(free.getId(), used.getId()), user);

        assertEquals(1, result.getDeletedCount(), "the unreferenced one still goes");
        assertTrue(result.hasBlocked());
        assertEquals(List.of("Oats"), result.getBlockedNames());
        assertTrue(result.describeBlocked().contains("Oats"));

        assertEquals(1, ingredientService.findAll(user).size(),
                "the referenced ingredient survives");
    }

    @Test
    void countsIdsFromAnotherAccountAsMissingRatherThanDeletingThem() {
        User mine = cook("bulk-mine@example.com");
        User theirs = cook("bulk-theirs@example.com");
        Ingredient theirSaffron = ingredient(theirs, "Saffron");
        Ingredient myFlour = ingredient(mine, "Flour");

        BulkDeleteResult result = ingredientService.deleteAll(
                List.of(myFlour.getId(), theirSaffron.getId()), mine);

        assertEquals(1, result.getDeletedCount());
        assertEquals(1, result.getMissingCount());
        assertEquals(1, ingredientService.findAll(theirs).size(),
                "the other account's ingredient is untouched");
    }

    @Test
    void handlesAnEmptyOrNullSelection() {
        User user = cook("bulk-empty@example.com");
        ingredient(user, "Alpha");

        assertEquals(0, ingredientService.deleteAll(List.of(), user).getDeletedCount());
        assertEquals(0, ingredientService.deleteAll(null, user).getDeletedCount());
        assertEquals(1, ingredientService.findAll(user).size());
    }

    @Test
    void deletesEverySelectedPantryItem() {
        User user = cook("bulk-pantry@example.com");
        Ingredient flour = ingredient(user, "Flour");
        PantryItem first = stock(user, flour, "500");
        PantryItem second = stock(user, flour, "250");
        stock(user, flour, "100");

        BulkDeleteResult result = pantryService.deleteAll(
                List.of(first.getId(), second.getId()), user);

        assertEquals(2, result.getDeletedCount());
        assertFalse(result.hasBlocked(), "nothing references a pantry row");
        assertEquals(1, pantryService.findAll(user).size());
    }

    @Test
    void deletesSelectedRecipesWithoutCrossingAccountBoundaries() {
        User mine = cook("bulk-recipes-mine@example.com");
        User theirs = cook("bulk-recipes-theirs@example.com");
        Recipe first = recipe(mine, "First recipe");
        Recipe second = recipe(mine, "Second recipe");
        Recipe theirRecipe = recipe(theirs, "Private recipe");

        BulkDeleteResult result = recipeService.deleteAll(
                List.of(first.getId(), second.getId(), theirRecipe.getId()), mine);

        assertEquals(2, result.getDeletedCount());
        assertEquals(1, result.getMissingCount());
        assertTrue(recipeService.findAll(mine).isEmpty());
        assertEquals(1, recipeService.findAll(theirs).size());
    }

    @Test
    void keepsScheduledRecipesWhileDeletingOtherSelectedRecipes() {
        User user = cook("bulk-recipes-scheduled@example.com");
        Recipe free = recipe(user, "Free recipe");
        Recipe scheduled = recipe(user, "Scheduled recipe");
        MealPlanForm planForm = new MealPlanForm();
        planForm.setName("This week");
        planForm.setWeekStartDate(LocalDate.of(2026, 9, 14));
        MealPlan plan = mealPlanService.create(planForm, user);
        PlanEntryForm entry = new PlanEntryForm();
        entry.setRecipeId(scheduled.getId());
        entry.setPlanDate(plan.getWeekStartDate());
        entry.setMealSlot(MealSlot.DINNER);
        entry.setServings(4);
        mealPlanService.addEntry(plan.getId(), entry, user);

        BulkDeleteResult result = recipeService.deleteAll(
                List.of(free.getId(), scheduled.getId()), user);

        assertEquals(1, result.getDeletedCount());
        assertEquals(List.of("Scheduled recipe"), result.getBlockedNames());
        assertEquals(List.of("Scheduled recipe"), recipeService.findAll(user).stream()
                .map(Recipe::getName)
                .toList());
    }

    @Test
    void deletingPantryRowsLeavesTheIngredientAlone() {
        User user = cook("bulk-pantry-ing@example.com");
        Ingredient flour = ingredient(user, "Flour");
        PantryItem row = stock(user, flour, "500");

        pantryService.deleteAll(List.of(row.getId()), user);

        assertTrue(pantryService.findAll(user).isEmpty());
        assertEquals(1, ingredientService.findAll(user).size(),
                "clearing the shelf does not remove the ingredient itself");
    }

    @Test
    void wordsTheBlockedMessageForOneAndForSeveral() {
        User user = cook("bulk-wording@example.com");
        Ingredient oats = ingredient(user, "Oats");
        Ingredient rice = ingredient(user, "Rice");
        recipeUsing(user, "Porridge", oats);
        recipeUsing(user, "Rice bowl", rice);

        BulkDeleteResult single = ingredientService.deleteAll(List.of(oats.getId()), user);
        assertTrue(single.describeBlocked().startsWith("Oats was kept"),
                "one blocked record reads in the singular");

        BulkDeleteResult several =
                ingredientService.deleteAll(List.of(oats.getId(), rice.getId()), user);
        assertTrue(several.describeBlocked().startsWith("2 were kept"),
                "several blocked records are counted and listed");
        assertTrue(several.describeBlocked().contains("Rice"));
    }
}
