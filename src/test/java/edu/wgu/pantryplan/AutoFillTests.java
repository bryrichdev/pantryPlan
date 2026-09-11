package edu.wgu.pantryplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.IngredientCategory;
import edu.wgu.pantryplan.domain.MealPlan;
import edu.wgu.pantryplan.domain.MealSlot;
import edu.wgu.pantryplan.domain.PlanEntry;
import edu.wgu.pantryplan.domain.Recipe;
import edu.wgu.pantryplan.domain.StorageLocation;
import edu.wgu.pantryplan.domain.Unit;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.service.IngredientService;
import edu.wgu.pantryplan.service.MealPlanService;
import edu.wgu.pantryplan.service.PantryCoverageService;
import edu.wgu.pantryplan.service.PantryService;
import edu.wgu.pantryplan.service.RecipeService;
import edu.wgu.pantryplan.service.UserService;
import edu.wgu.pantryplan.web.form.AutoFillForm;
import edu.wgu.pantryplan.web.form.IngredientForm;
import edu.wgu.pantryplan.web.form.MealPlanForm;
import edu.wgu.pantryplan.web.form.PantryItemForm;
import edu.wgu.pantryplan.web.form.PlanEntryForm;
import edu.wgu.pantryplan.web.form.RecipeForm;
import edu.wgu.pantryplan.web.form.RecipeLineForm;
import edu.wgu.pantryplan.web.form.RegistrationForm;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auto-fill is randomised, so these tests either pass a seeded Random for an
 * exact outcome or assert on properties that must hold for any shuffle.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AutoFillTests {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);

    @Autowired
    private MealPlanService mealPlanService;

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private IngredientService ingredientService;

    @Autowired
    private PantryService pantryService;

    @Autowired
    private PantryCoverageService coverageService;

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

    private Recipe recipeUsing(User user, String name, Ingredient ingredient, String grams) {
        RecipeForm form = new RecipeForm();
        form.setName(name);
        form.setServings(4);
        if (ingredient != null) {
            RecipeLineForm line = new RecipeLineForm();
            line.setIngredientId(ingredient.getId());
            line.setQuantity(new BigDecimal(grams));
            line.setUnit(Unit.GRAM);
            form.getLines().add(line);
        }
        return recipeService.create(form, user);
    }

    private Recipe recipeForMealType(User user, String name, String mealType) {
        RecipeForm form = new RecipeForm();
        form.setName(name);
        form.setServings(4);
        form.setMealType(mealType);
        return recipeService.create(form, user);
    }

    private void stock(User user, Ingredient ingredient, String grams) {
        PantryItemForm form = new PantryItemForm();
        form.setIngredientId(ingredient.getId());
        form.setQuantity(new BigDecimal(grams));
        form.setLocation(StorageLocation.PANTRY);
        pantryService.create(form, user);
    }

    private MealPlan plan(User user, String name) {
        MealPlanForm form = new MealPlanForm();
        form.setName(name);
        form.setWeekStartDate(MONDAY);
        return mealPlanService.create(form, user);
    }

    private AutoFillForm options(Set<MealSlot> slots, boolean favorPantry, boolean avoidRepeats) {
        AutoFillForm form = new AutoFillForm();
        form.setSlots(slots);
        form.setServings(4);
        form.setFavorPantry(favorPantry);
        form.setAvoidRepeats(avoidRepeats);
        return form;
    }

    @Test
    void fillsEveryDinnerSlotInTheWeek() {
        User user = cook("fill-dinner@example.com");
        Ingredient oats = ingredient(user, "Oats");
        recipeUsing(user, "Porridge", oats, "80");
        recipeUsing(user, "Flapjacks", oats, "200");

        MealPlan created = plan(user, "Filled week");
        int added = mealPlanService.autoFill(created.getId(),
                options(Set.of(MealSlot.DINNER), false, true), user, new Random(42));

        assertEquals(7, added, "one dinner per day");
        List<PlanEntry> entries = mealPlanService.entriesInOrder(
                mealPlanService.requireOwned(created.getId(), user));
        assertEquals(7, entries.size());
        assertTrue(entries.stream().allMatch(entry -> entry.getMealSlot() == MealSlot.DINNER));
    }

    @Test
    void fillsDinnerOnlyWithDinnerRecipes() {
        User user = cook("fill-matching-types@example.com");
        recipeForMealType(user, "Cinnamon French Toast", "Breakfast");
        recipeForMealType(user, "Spaghetti Marinara", "Dinner");

        MealPlan created = plan(user, "Type-aware week");
        int added = mealPlanService.autoFill(created.getId(),
                options(Set.of(MealSlot.DINNER), false, true), user, new Random(21));

        List<PlanEntry> entries = mealPlanService.entriesInOrder(
                mealPlanService.requireOwned(created.getId(), user));
        assertEquals(7, added);
        assertTrue(entries.stream().allMatch(entry -> "Dinner"
                .equalsIgnoreCase(entry.getRecipe().getMealType())));
        assertFalse(entries.stream().anyMatch(entry -> entry.getRecipe().getName()
                .equals("Cinnamon French Toast")));
    }

    @Test
    void fillsSeveralMealsPerDayWhenAsked() {
        User user = cook("fill-multi@example.com");
        Ingredient rice = ingredient(user, "Rice");
        recipeUsing(user, "Rice bowl", rice, "100");

        MealPlan created = plan(user, "Busy week");
        int added = mealPlanService.autoFill(created.getId(),
                options(Set.of(MealSlot.LUNCH, MealSlot.DINNER), false, true), user, new Random(7));

        assertEquals(14, added, "seven days times two meals");
    }

    @Test
    void leavesScheduledMealsAloneByDefault() {
        User user = cook("fill-gaps@example.com");
        Ingredient beans = ingredient(user, "Beans");
        recipeUsing(user, "Chili", beans, "400");
        Recipe special = recipeUsing(user, "Birthday roast", beans, "100");

        MealPlan created = plan(user, "Partly planned");
        PlanEntryForm manual = new PlanEntryForm();
        manual.setRecipeId(special.getId());
        manual.setPlanDate(MONDAY.plusDays(2));
        manual.setMealSlot(MealSlot.DINNER);
        manual.setServings(8);
        mealPlanService.addEntry(created.getId(), manual, user);

        int added = mealPlanService.autoFill(created.getId(),
                options(Set.of(MealSlot.DINNER), false, true), user, new Random(1));

        assertEquals(6, added, "the six empty dinners, not the one already set");

        PlanEntry wednesday = mealPlanService.entriesInOrder(
                        mealPlanService.requireOwned(created.getId(), user)).stream()
                .filter(entry -> entry.getPlanDate().equals(MONDAY.plusDays(2)))
                .findFirst()
                .orElseThrow();
        assertEquals("Birthday roast", wednesday.getRecipe().getName());
        assertEquals(8, wednesday.getServings(), "the hand-set servings survive");
    }

    @Test
    void replacesScheduledMealsWhenAsked() {
        User user = cook("fill-replace@example.com");
        Ingredient beans = ingredient(user, "Beans");
        recipeUsing(user, "Chili", beans, "400");
        Recipe old = recipeUsing(user, "Old favourite", beans, "100");

        MealPlan created = plan(user, "Overwritten week");
        PlanEntryForm manual = new PlanEntryForm();
        manual.setRecipeId(old.getId());
        manual.setPlanDate(MONDAY);
        manual.setMealSlot(MealSlot.DINNER);
        manual.setServings(2);
        mealPlanService.addEntry(created.getId(), manual, user);

        AutoFillForm form = options(Set.of(MealSlot.DINNER), false, true);
        form.setReplaceExisting(true);
        int added = mealPlanService.autoFill(created.getId(), form, user, new Random(3));

        assertEquals(7, added, "all seven dinners are filled afresh");
        assertEquals(7, mealPlanService.entriesInOrder(
                mealPlanService.requireOwned(created.getId(), user)).size(),
                "the replaced meal is gone rather than doubled up");
    }

    @Test
    void worksThroughEveryRecipeBeforeRepeating() {
        User user = cook("fill-norepeat@example.com");
        Ingredient salt = ingredient(user, "Salt");
        for (int i = 1; i <= 7; i++) {
            recipeUsing(user, "Recipe " + i, salt, "5");
        }

        MealPlan created = plan(user, "Varied week");
        mealPlanService.autoFill(created.getId(),
                options(Set.of(MealSlot.DINNER), false, true), user, new Random(11));

        Set<String> names = new HashSet<>();
        mealPlanService.entriesInOrder(mealPlanService.requireOwned(created.getId(), user))
                .forEach(entry -> names.add(entry.getRecipe().getName()));

        assertEquals(7, names.size(), "seven recipes across seven dinners, no repeats");
    }

    @Test
    void repeatsOnlyAfterExhaustingTheList() {
        User user = cook("fill-fewrecipes@example.com");
        Ingredient salt = ingredient(user, "Salt");
        recipeUsing(user, "Only one", salt, "5");

        MealPlan created = plan(user, "Monotonous week");
        int added = mealPlanService.autoFill(created.getId(),
                options(Set.of(MealSlot.DINNER), false, true), user, new Random(5));

        assertEquals(7, added, "a single recipe still fills the week");
    }

    @Test
    void favouringThePantryPrefersRecipesAlreadyCovered() {
        User user = cook("fill-coverage@example.com");
        Ingredient stocked = ingredient(user, "Stocked grain");
        Ingredient missing = ingredient(user, "Missing spice");
        stock(user, stocked, "5000");

        recipeUsing(user, "Uses what you have", stocked, "100");
        recipeUsing(user, "Needs a shop", missing, "100");

        MealPlan created = plan(user, "Pantry-led week");
        mealPlanService.autoFill(created.getId(),
                options(Set.of(MealSlot.DINNER), true, true), user, new Random(2));

        PlanEntry monday = mealPlanService.entriesInOrder(
                mealPlanService.requireOwned(created.getId(), user)).get(0);
        assertEquals("Uses what you have", monday.getRecipe().getName(),
                "the covered recipe is scheduled first");
    }

    @Test
    void addsNothingWhenThereAreNoRecipes() {
        User user = cook("fill-norecipes@example.com");
        MealPlan created = plan(user, "Empty larder");

        int added = mealPlanService.autoFill(created.getId(),
                options(Set.of(MealSlot.DINNER), true, true), user, new Random(9));

        assertEquals(0, added);
    }

    @Test
    void scoresCoverageFromWhatIsOnTheShelf() {
        User user = cook("coverage-score@example.com");
        Ingredient have = ingredient(user, "Have this");
        Ingredient lack = ingredient(user, "Lack this");
        stock(user, have, "1000");

        Recipe covered = recipeUsing(user, "Fully covered", have, "100");
        Recipe uncovered = recipeUsing(user, "Not covered", lack, "100");

        assertEquals(0, BigDecimal.ONE.compareTo(
                coverageService.coverageOf(user, covered, 4)),
                "every ingredient on the shelf scores one");
        assertEquals(0, BigDecimal.ZERO.compareTo(
                coverageService.coverageOf(user, uncovered, 4)),
                "nothing on the shelf scores zero");
    }

    @Test
    void coverageAccountsForScaledServings() {
        User user = cook("coverage-scale@example.com");
        Ingredient flour = ingredient(user, "Flour");
        stock(user, flour, "400");
        Recipe bread = recipeUsing(user, "Bread", flour, "200");

        assertTrue(coverageService.covers(user, bread.getLines().get(0), 4),
                "200 g needed against 400 g on hand");
        assertFalse(coverageService.covers(user, bread.getLines().get(0), 12),
                "tripling the servings needs 600 g, which is more than the shelf holds");
    }

    @Test
    void anEmptyRecipeScoresZeroRatherThanFullyCovered() {
        User user = cook("coverage-empty@example.com");
        Recipe blank = recipeUsing(user, "No ingredients", null, null);

        assertEquals(0, BigDecimal.ZERO.compareTo(coverageService.coverageOf(user, blank, 4)),
                "an empty recipe must not outrank real ones");
    }
}
