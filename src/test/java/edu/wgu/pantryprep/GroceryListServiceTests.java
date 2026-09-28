package edu.wgu.pantryprep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryprep.domain.GroceryList;
import edu.wgu.pantryprep.domain.GroceryListItem;
import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.IngredientCategory;
import edu.wgu.pantryprep.domain.MealPlan;
import edu.wgu.pantryprep.domain.MealSlot;
import edu.wgu.pantryprep.domain.PlanEntry;
import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.StorageLocation;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.repository.GroceryListRepository;
import edu.wgu.pantryprep.service.GroceryListService;
import edu.wgu.pantryprep.service.IngredientService;
import edu.wgu.pantryprep.service.MealPlanService;
import edu.wgu.pantryprep.service.PantryCoverageService;
import edu.wgu.pantryprep.service.PantryService;
import edu.wgu.pantryprep.service.RecipeService;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.IngredientForm;
import edu.wgu.pantryprep.web.form.MealPlanForm;
import edu.wgu.pantryprep.web.form.PantryItemForm;
import edu.wgu.pantryprep.web.form.PlanEntryForm;
import edu.wgu.pantryprep.web.form.RecipeForm;
import edu.wgu.pantryprep.web.form.RecipeLineForm;
import edu.wgu.pantryprep.web.form.RegistrationForm;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class GroceryListServiceTests {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);

    @Autowired
    private GroceryListService groceryListService;

    @Autowired
    private GroceryListRepository groceryListRepository;

    @Autowired
    private PantryCoverageService coverageService;

    @Autowired
    private MealPlanService mealPlanService;

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private IngredientService ingredientService;

    @Autowired
    private PantryService pantryService;

    @Autowired
    private UserService userService;

    /* ------------------------------------------------------------ helpers */

    private User cook(String email) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName("Cook");
        form.setEmail(email);
        form.setPassword("correcthorsebattery");
        form.setConfirmPassword("correcthorsebattery");
        return userService.register(form);
    }

    private Ingredient ingredient(User user, String name, Unit stockUnit, String gramsPerCup) {
        IngredientForm form = new IngredientForm();
        form.setName(name);
        form.setCategory(IngredientCategory.PANTRY_STAPLE);
        form.setStockUnit(stockUnit);
        form.setDefaultLocation(StorageLocation.PANTRY);
        if (gramsPerCup != null) {
            form.setGramsPerCup(new BigDecimal(gramsPerCup));
        }
        return ingredientService.create(form, user);
    }

    /** A recipe with a single ingredient line. */
    private Recipe recipe(User user, String name, int servings, Ingredient ingredient,
                          String quantity, Unit unit) {
        RecipeForm form = new RecipeForm();
        form.setName(name);
        form.setServings(servings);
        RecipeLineForm line = new RecipeLineForm();
        line.setIngredientId(ingredient.getId());
        line.setQuantity(new BigDecimal(quantity));
        line.setUnit(unit);
        form.getLines().add(line);
        return recipeService.create(form, user);
    }

    private MealPlan plan(User user) {
        MealPlanForm form = new MealPlanForm();
        form.setName("Test week");
        form.setWeekStartDate(MONDAY);
        return mealPlanService.create(form, user);
    }

    private PlanEntry schedule(User user, MealPlan plan, Recipe recipe, LocalDate day, int servings) {
        PlanEntryForm form = new PlanEntryForm();
        form.setRecipeId(recipe.getId());
        form.setPlanDate(day);
        form.setMealSlot(MealSlot.DINNER);
        form.setServings(servings);
        return mealPlanService.addEntry(plan.getId(), form, user);
    }

    private void stock(User user, Ingredient ingredient, String quantity, LocalDate expiresOn) {
        PantryItemForm form = new PantryItemForm();
        form.setIngredientId(ingredient.getId());
        form.setQuantity(new BigDecimal(quantity));
        form.setLocation(StorageLocation.PANTRY);
        form.setExpiresOn(expiresOn);
        pantryService.create(form, user);
    }

    private static void assertAmount(String expected, BigDecimal actual, String message) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                message + " (expected " + expected + ", was " + actual + ")");
    }

    /* -------------------------------------------------------------- tests */

    /**
     * The reason the list adds up the week first. Each meal fits inside the
     * pound of flour on its own, so a line-by-line check would buy nothing.
     * Together they need 600 g, which is 1.323 lb.
     */
    @Test
    void sumsTheWeekBeforeSubtractingThePantry() {
        User user = cook("grocery-sum@example.com");
        Ingredient flour = ingredient(user, "Flour", Unit.POUND, "120");
        Recipe pancakes = recipe(user, "Pancakes", 4, flour, "2", Unit.CUP);
        Recipe bread = recipe(user, "Bread", 4, flour, "3", Unit.CUP);
        stock(user, flour, "1", null);

        MealPlan week = plan(user);
        schedule(user, week, pancakes, MONDAY, 4);
        schedule(user, week, bread, MONDAY.plusDays(2), 4);

        assertTrue(coverageService.covers(user, pancakes.getLines().get(0), 4),
                "pancakes alone fit inside the pound on the shelf");
        assertTrue(coverageService.covers(user, bread.getLines().get(0), 4),
                "bread alone fits inside the pound on the shelf");

        GroceryList list = groceryListService.generate(week.getId(), user, MONDAY);

        assertEquals("Groceries for Test week", list.getTitle());
        assertEquals(1, list.getItems().size());
        GroceryListItem item = list.getItems().get(0);
        assertAmount("0.323", item.getNeededQuantity(), "1.323 lb needed less 1 lb on hand");
        assertEquals(Unit.POUND, item.getUnit(), "listed in the ingredient's stock unit");
        assertEquals(IngredientCategory.PANTRY_STAPLE, item.getCategory());
        assertFalse(item.isNeedsReview());
    }

    @Test
    void scalesEachMealToItsPlannedServings() {
        User user = cook("grocery-scale@example.com");
        Ingredient rice = ingredient(user, "Rice", Unit.GRAM, null);
        Recipe pilaf = recipe(user, "Pilaf", 2, rice, "100", Unit.GRAM);
        stock(user, rice, "50", null);

        MealPlan week = plan(user);
        schedule(user, week, pilaf, MONDAY, 6);

        GroceryList list = groceryListService.generate(week.getId(), user, MONDAY);

        assertEquals(1, list.getItems().size());
        assertAmount("250", list.getItems().get(0).getNeededQuantity(),
                "100 g for two, cooked for six, is 300 g, less 50 g on hand");
    }

    @Test
    void leavesOutWhatThePantryAlreadyCovers() {
        User user = cook("grocery-covered@example.com");
        Ingredient oats = ingredient(user, "Oats", Unit.GRAM, null);
        Recipe porridge = recipe(user, "Porridge", 2, oats, "80", Unit.GRAM);
        stock(user, oats, "500", null);

        MealPlan week = plan(user);
        schedule(user, week, porridge, MONDAY, 2);

        GroceryList list = groceryListService.generate(week.getId(), user, MONDAY);

        assertTrue(list.getItems().isEmpty(), "nothing to buy when the shelf covers the week");
    }

    /**
     * Basil kept in grams with no weight per cup. The gram line converts and is
     * reduced by the pantry. The cup line cannot, so it comes through whole, in
     * cups, flagged for the cook to check.
     */
    @Test
    void flagsLinesWhoseUnitsCannotBeReconciled() {
        User user = cook("grocery-flag@example.com");
        Ingredient basil = ingredient(user, "Basil", Unit.GRAM, null);
        Recipe pesto = recipe(user, "Pesto", 4, basil, "2", Unit.CUP);
        Recipe salad = recipe(user, "Salad", 4, basil, "50", Unit.GRAM);
        stock(user, basil, "20", null);

        MealPlan week = plan(user);
        schedule(user, week, pesto, MONDAY, 4);
        schedule(user, week, salad, MONDAY.plusDays(1), 4);

        List<GroceryListItem> items = groceryListService.generate(week.getId(), user, MONDAY).getItems();
        assertEquals(2, items.size(), "one converted row and one flagged row");

        GroceryListItem converted = items.stream().filter(i -> !i.isNeedsReview()).findFirst().orElseThrow();
        assertEquals(Unit.GRAM, converted.getUnit());
        assertAmount("30", converted.getNeededQuantity(), "50 g less the 20 g on hand");

        GroceryListItem flagged = items.stream().filter(GroceryListItem::isNeedsReview).findFirst().orElseThrow();
        assertEquals(Unit.CUP, flagged.getUnit(), "kept in the recipe's unit");
        assertAmount("2", flagged.getNeededQuantity(), "the pantry is not subtracted from a flagged row");
    }

    @Test
    void skipsMealsAlreadyCooked() {
        User user = cook("grocery-cooked@example.com");
        Ingredient pasta = ingredient(user, "Pasta", Unit.GRAM, null);
        Recipe spaghetti = recipe(user, "Spaghetti", 4, pasta, "400", Unit.GRAM);

        MealPlan week = plan(user);
        PlanEntry monday = schedule(user, week, spaghetti, MONDAY, 4);
        schedule(user, week, spaghetti, MONDAY.plusDays(3), 4);
        monday.markCooked(Instant.now());

        GroceryList list = groceryListService.generate(week.getId(), user, MONDAY);

        assertEquals(1, list.getItems().size());
        assertAmount("400", list.getItems().get(0).getNeededQuantity(),
                "only the meal still to cook is counted");
    }

    @Test
    void ignoresExpiredStock() {
        User user = cook("grocery-expired@example.com");
        Ingredient milk = ingredient(user, "Milk", Unit.MILLILITER, null);
        Recipe custard = recipe(user, "Custard", 4, milk, "200", Unit.MILLILITER);
        stock(user, milk, "500", MONDAY.minusDays(1));
        stock(user, milk, "50", MONDAY);

        assertAmount("50", coverageService.onHandOf(user, milk, MONDAY),
                "the expired carton does not count; one expiring today still does");

        MealPlan week = plan(user);
        schedule(user, week, custard, MONDAY, 4);

        GroceryList list = groceryListService.generate(week.getId(), user, MONDAY);

        assertEquals(1, list.getItems().size());
        assertAmount("150", list.getItems().get(0).getNeededQuantity(), "200 ml less the 50 ml still good");
    }

    @Test
    void generatingAgainReplacesThePlansList() {
        User user = cook("grocery-replace@example.com");
        Ingredient beans = ingredient(user, "Beans", Unit.GRAM, null);
        Recipe chili = recipe(user, "Chili", 4, beans, "300", Unit.GRAM);

        MealPlan week = plan(user);
        schedule(user, week, chili, MONDAY, 4);

        GroceryList first = groceryListService.generate(week.getId(), user, MONDAY);
        GroceryList second = groceryListService.generate(week.getId(), user, MONDAY);

        List<GroceryList> saved = groceryListRepository.findAllByMealPlanOrderByGeneratedAtDesc(week);
        assertEquals(1, saved.size(), "a plan keeps one list");
        assertEquals(second.getId(), saved.get(0).getId());
        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void refusesAnotherAccountsPlan() {
        User owner = cook("grocery-owner@example.com");
        User stranger = cook("grocery-stranger@example.com");
        MealPlan week = plan(owner);

        assertThrows(NoSuchElementException.class,
                () -> groceryListService.generate(week.getId(), stranger, MONDAY));
    }

    @Test
    void togglesPurchasedForTheOwnerOnly() {
        User owner = cook("grocery-tick@example.com");
        User stranger = cook("grocery-tick-stranger@example.com");
        Ingredient eggs = ingredient(owner, "Eggs", Unit.PIECE, null);
        Recipe omelette = recipe(owner, "Omelette", 2, eggs, "3", Unit.PIECE);

        MealPlan week = plan(owner);
        schedule(owner, week, omelette, MONDAY, 2);
        GroceryList list = groceryListService.generate(week.getId(), owner, MONDAY);
        Long itemId = list.getItems().get(0).getId();

        assertTrue(groceryListService.togglePurchased(list.getId(), itemId, owner).isPurchased());
        assertFalse(groceryListService.togglePurchased(list.getId(), itemId, owner).isPurchased(),
                "a second tick undoes the first");

        assertThrows(NoSuchElementException.class,
                () -> groceryListService.togglePurchased(list.getId(), itemId, stranger),
                "another account cannot tick items on this list");
    }

    @Test
    void findsThePlansCurrentList() {
        User user = cook("grocery-current@example.com");
        Ingredient lentils = ingredient(user, "Lentils", Unit.GRAM, null);
        Recipe dal = recipe(user, "Dal", 4, lentils, "250", Unit.GRAM);

        MealPlan week = plan(user);
        assertTrue(groceryListService.findForPlan(week).isEmpty(), "no list before one is built");

        schedule(user, week, dal, MONDAY, 4);
        GroceryList built = groceryListService.generate(week.getId(), user, MONDAY);

        assertEquals(built.getId(), groceryListService.findForPlan(week).orElseThrow().getId());
        assertEquals(1, groceryListService.findAll(user).size());
    }

    @Test
    void deletesTheOwnersListOnly() {
        User owner = cook("grocery-delete@example.com");
        User stranger = cook("grocery-delete-stranger@example.com");
        MealPlan week = plan(owner);
        GroceryList list = groceryListService.generate(week.getId(), owner, MONDAY);

        assertThrows(NoSuchElementException.class,
                () -> groceryListService.delete(list.getId(), stranger));

        groceryListService.delete(list.getId(), owner);
        assertNull(groceryListService.findForPlan(week).orElse(null));
    }
}
