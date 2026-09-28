package edu.wgu.pantryprep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryprep.domain.MealPlan;
import edu.wgu.pantryprep.domain.MealSlot;
import edu.wgu.pantryprep.domain.PlanEntry;
import edu.wgu.pantryprep.domain.CookLog;
import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.IngredientCategory;
import edu.wgu.pantryprep.domain.PantryItem;
import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.StorageLocation;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.repository.CookLogRepository;
import edu.wgu.pantryprep.service.BulkDeleteResult;
import edu.wgu.pantryprep.service.CookResult;
import edu.wgu.pantryprep.service.CookUndoResult;
import edu.wgu.pantryprep.service.IngredientInUseException;
import edu.wgu.pantryprep.service.IngredientService;
import edu.wgu.pantryprep.service.MealPlanService;
import edu.wgu.pantryprep.service.PantryService;
import edu.wgu.pantryprep.service.RecipeService;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.MealPlanForm;
import edu.wgu.pantryprep.web.form.IngredientForm;
import edu.wgu.pantryprep.web.form.PantryItemForm;
import edu.wgu.pantryprep.web.form.PlanEntryForm;
import edu.wgu.pantryprep.web.form.RecipeForm;
import edu.wgu.pantryprep.web.form.RecipeLineForm;
import edu.wgu.pantryprep.web.form.RegistrationForm;
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
class MealPlanServiceTests {

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
    private CookLogRepository cookLogRepository;

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

    private Recipe recipe(User user, String name, int servings) {
        RecipeForm form = new RecipeForm();
        form.setName(name);
        form.setServings(servings);
        return recipeService.create(form, user);
    }

    private MealPlan plan(User user, String name) {
        MealPlanForm form = new MealPlanForm();
        form.setName(name);
        form.setWeekStartDate(MONDAY);
        return mealPlanService.create(form, user);
    }

    private PlanEntryForm entryFor(Recipe recipe, LocalDate date, MealSlot slot, int servings) {
        PlanEntryForm form = new PlanEntryForm();
        form.setRecipeId(recipe.getId());
        form.setPlanDate(date);
        form.setMealSlot(slot);
        form.setServings(servings);
        return form;
    }

    private Ingredient ingredient(User user, String name, Unit stockUnit) {
        IngredientForm form = new IngredientForm();
        form.setName(name);
        form.setCategory(IngredientCategory.OTHER);
        form.setStockUnit(stockUnit);
        form.setDefaultLocation(StorageLocation.PANTRY);
        return ingredientService.create(form, user);
    }

    private PantryItem stock(User user, Ingredient ingredient, String quantity) {
        PantryItemForm form = new PantryItemForm();
        form.setIngredientId(ingredient.getId());
        form.setQuantity(new java.math.BigDecimal(quantity));
        form.setLocation(StorageLocation.PANTRY);
        return pantryService.create(form, user);
    }

    private Recipe recipeUsing(User user, String name, int servings, Ingredient ingredient,
                               String quantity, Unit unit) {
        RecipeForm form = new RecipeForm();
        form.setName(name);
        form.setServings(servings);
        RecipeLineForm line = new RecipeLineForm();
        line.setIngredientId(ingredient.getId());
        line.setQuantity(new java.math.BigDecimal(quantity));
        line.setUnit(unit);
        form.getLines().add(line);
        return recipeService.create(form, user);
    }

    @Test
    void createsAPlanCoveringSevenDays() {
        User user = cook("plan-create@example.com");
        MealPlan created = plan(user, "Week of the 14th");

        assertEquals(MONDAY, created.getWeekStartDate());
        assertEquals(LocalDate.of(2026, 9, 20), created.weekEndDate());
        assertEquals(7, mealPlanService.weekDates(created).size());
    }

    @Test
    void keepsPlansScopedToTheOwningAccount() {
        User mine = cook("plan-mine@example.com");
        User theirs = cook("plan-theirs@example.com");
        MealPlan secret = plan(mine, "Private week");

        assertTrue(mealPlanService.findAll(theirs).isEmpty());
        assertThrows(NoSuchElementException.class,
                () -> mealPlanService.requireOwned(secret.getId(), theirs));
    }

    @Test
    void addsEntriesAndReturnsThemInDayThenMealOrder() {
        User user = cook("plan-order@example.com");
        MealPlan created = plan(user, "Ordered week");
        Recipe eggs = recipe(user, "Scrambled eggs", 2);
        Recipe sandwich = recipe(user, "Turkey sandwich", 1);
        Recipe stew = recipe(user, "Beef stew", 6);

        mealPlanService.addEntry(created.getId(),
                entryFor(stew, MONDAY.plusDays(1), MealSlot.DINNER, 6), user);
        mealPlanService.addEntry(created.getId(),
                entryFor(eggs, MONDAY, MealSlot.BREAKFAST, 2), user);
        mealPlanService.addEntry(created.getId(),
                entryFor(sandwich, MONDAY, MealSlot.LUNCH, 1), user);
        mealPlanService.addEntry(created.getId(),
                entryFor(stew, MONDAY, MealSlot.DINNER, 4), user);

        List<PlanEntry> ordered = mealPlanService.entriesInOrder(
                mealPlanService.requireOwned(created.getId(), user));

        assertEquals(4, ordered.size());
        assertEquals(MONDAY, ordered.get(0).getPlanDate());
        assertEquals(MealSlot.BREAKFAST, ordered.get(0).getMealSlot(),
                "breakfast comes first on the same day");
        assertEquals(MealSlot.LUNCH, ordered.get(1).getMealSlot());
        assertEquals(MealSlot.DINNER, ordered.get(2).getMealSlot());
        assertEquals(MONDAY.plusDays(1), ordered.get(3).getPlanDate());
    }

    @Test
    void allowsTheSameRecipeTwiceInOneWeek() {
        User user = cook("plan-repeat@example.com");
        MealPlan created = plan(user, "Leftovers week");
        Recipe chili = recipe(user, "Chili", 8);

        mealPlanService.addEntry(created.getId(), entryFor(chili, MONDAY, MealSlot.DINNER, 8), user);
        mealPlanService.addEntry(created.getId(),
                entryFor(chili, MONDAY.plusDays(2), MealSlot.LUNCH, 2), user);

        assertEquals(2, mealPlanService.entriesInOrder(
                mealPlanService.requireOwned(created.getId(), user)).size());
    }

    @Test
    void recordsServingsThatDifferFromTheRecipeDefault() {
        User user = cook("plan-servings@example.com");
        MealPlan created = plan(user, "Scaled week");
        Recipe pasta = recipe(user, "Pasta", 4);

        PlanEntry entry = mealPlanService.addEntry(created.getId(),
                entryFor(pasta, MONDAY, MealSlot.DINNER, 10), user);

        assertEquals(10, entry.getServings());
        assertEquals(4, entry.getRecipe().getServings(),
                "the recipe itself is unchanged");
    }

    @Test
    void knowsWhichDatesFallInsideThePlanWeek() {
        User user = cook("plan-window@example.com");
        MealPlan created = plan(user, "Bounded week");

        assertTrue(mealPlanService.dateIsInWeek(created, MONDAY));
        assertTrue(mealPlanService.dateIsInWeek(created, MONDAY.plusDays(6)));
        assertFalse(mealPlanService.dateIsInWeek(created, MONDAY.minusDays(1)));
        assertFalse(mealPlanService.dateIsInWeek(created, MONDAY.plusDays(7)));
        assertFalse(mealPlanService.dateIsInWeek(created, null));
    }

    @Test
    void removesOneEntryWithoutTouchingTheOthers() {
        User user = cook("plan-remove@example.com");
        MealPlan created = plan(user, "Trimmed week");
        Recipe soup = recipe(user, "Soup", 4);

        PlanEntry first = mealPlanService.addEntry(created.getId(),
                entryFor(soup, MONDAY, MealSlot.LUNCH, 4), user);
        mealPlanService.addEntry(created.getId(),
                entryFor(soup, MONDAY.plusDays(1), MealSlot.LUNCH, 4), user);

        mealPlanService.removeEntry(created.getId(), first.getId(), user);

        List<PlanEntry> remaining = mealPlanService.entriesInOrder(
                mealPlanService.requireOwned(created.getId(), user));
        assertEquals(1, remaining.size());
        assertEquals(MONDAY.plusDays(1), remaining.get(0).getPlanDate());
    }

    @Test
    void refusesARecipeBelongingToAnotherAccount() {
        User mine = cook("plan-foreign-mine@example.com");
        User theirs = cook("plan-foreign-theirs@example.com");
        MealPlan created = plan(mine, "My week");
        Recipe theirRecipe = recipe(theirs, "Their roast", 6);

        assertThrows(NoSuchElementException.class,
                () -> mealPlanService.addEntry(created.getId(),
                        entryFor(theirRecipe, MONDAY, MealSlot.DINNER, 6), mine));
    }

    @Test
    void refusesToRemoveAnEntryFromAnotherPlan() {
        User user = cook("plan-crossentry@example.com");
        MealPlan first = plan(user, "First week");
        MealPlanForm secondForm = new MealPlanForm();
        secondForm.setName("Second week");
        secondForm.setWeekStartDate(MONDAY.plusDays(7));
        MealPlan second = mealPlanService.create(secondForm, user);

        Recipe toast = recipe(user, "Toast", 1);
        PlanEntry entry = mealPlanService.addEntry(first.getId(),
                entryFor(toast, MONDAY, MealSlot.BREAKFAST, 1), user);

        assertThrows(NoSuchElementException.class,
                () -> mealPlanService.removeEntry(second.getId(), entry.getId(), user));
    }

    @Test
    void deletingAPlanRemovesItsEntries() {
        User user = cook("plan-delete@example.com");
        MealPlan created = plan(user, "Doomed week");
        Recipe toast = recipe(user, "Toast", 1);
        mealPlanService.addEntry(created.getId(),
                entryFor(toast, MONDAY, MealSlot.BREAKFAST, 1), user);

        mealPlanService.delete(created.getId(), user);

        assertTrue(mealPlanService.findAll(user).isEmpty());
        assertFalse(recipeService.findAll(user).isEmpty(),
                "the recipe itself survives the plan being deleted");
    }

    @Test
    void cookingADinnerScalesIngredientsAndRemovesDepletedPantryRows() {
        User user = cook("plan-cook-deplete@example.com");
        Ingredient rice = ingredient(user, "Rice", Unit.GRAM);
        stock(user, rice, "50");
        stock(user, rice, "100");
        Recipe risotto = recipeUsing(user, "Risotto", 4, rice, "100", Unit.GRAM);
        MealPlan created = plan(user, "Cooked week");
        PlanEntry entry = mealPlanService.addEntry(created.getId(),
                entryFor(risotto, MONDAY, MealSlot.DINNER, 8), user);

        CookResult result = mealPlanService.markEntryCooked(created.getId(), entry.getId(), user);

        assertEquals(1, result.getCookedCount());
        assertTrue(pantryService.findAll(user).isEmpty(),
                "200 g needed for eight servings consumes the 150 g on hand without going below zero");
        PlanEntry cooked = mealPlanService.entriesInOrder(
                mealPlanService.requireOwned(created.getId(), user)).getFirst();
        assertTrue(cooked.isCooked());
        assertEquals(1, recipeService.requireOwned(risotto.getId(), user).getTimesCooked());
        List<CookLog> logs = cookLogRepository.findAllByPlanEntryAndReversedFalse(cooked);
        assertEquals(2, logs.size(),
                "each pantry row consumed is retained in the cooking audit");
        assertEquals(0, new java.math.BigDecimal("150.000").compareTo(logs.stream()
                .map(CookLog::getQuantityDeducted)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)));
    }

    @Test
    void cookingTheSameMealTwiceNeverDeductsPantryTwice() {
        User user = cook("plan-cook-repeat@example.com");
        Ingredient beans = ingredient(user, "Beans", Unit.GRAM);
        stock(user, beans, "300");
        Recipe chili = recipeUsing(user, "Chili", 4, beans, "100", Unit.GRAM);
        MealPlan created = plan(user, "Repeat-safe week");
        PlanEntry entry = mealPlanService.addEntry(created.getId(),
                entryFor(chili, MONDAY, MealSlot.DINNER, 4), user);

        CookResult first = mealPlanService.markEntryCooked(created.getId(), entry.getId(), user);
        CookResult second = mealPlanService.markEntryCooked(created.getId(), entry.getId(), user);

        assertEquals(1, first.getCookedCount());
        assertEquals(0, second.getCookedCount());
        assertEquals(1, second.getAlreadyCookedCount());
        assertEquals(new java.math.BigDecimal("200.000"),
                pantryService.findAll(user).getFirst().getQuantity());
        assertEquals(1, recipeService.requireOwned(chili.getId(), user).getTimesCooked());
    }

    @Test
    void undoingCookingRestoresOnlyWhatWasDeductedAndResetsMealHistory() {
        User user = cook("plan-undo-cook@example.com");
        Ingredient beans = ingredient(user, "Beans", Unit.GRAM);
        stock(user, beans, "50");
        Recipe chili = recipeUsing(user, "Chili", 4, beans, "100", Unit.GRAM);
        MealPlan created = plan(user, "Undo week");
        PlanEntry entry = mealPlanService.addEntry(created.getId(),
                entryFor(chili, MONDAY, MealSlot.DINNER, 4), user);

        mealPlanService.markEntryCooked(created.getId(), entry.getId(), user);
        CookUndoResult result = mealPlanService.markEntryNotCooked(
                created.getId(), entry.getId(), user);

        assertEquals(1, result.getUncookedCount());
        assertEquals(0, new java.math.BigDecimal("50.000").compareTo(
                pantryService.findAll(user).stream()
                        .map(PantryItem::getQuantity)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)),
                "undo restores the 50 g actually deducted, not the 100 g recipe requirement");
        PlanEntry restored = mealPlanService.entriesInOrder(
                mealPlanService.requireOwned(created.getId(), user)).getFirst();
        assertFalse(restored.isCooked());
        assertTrue(cookLogRepository.findAllByPlanEntryAndReversedFalse(restored).isEmpty(),
                "the original deduction logs are retained but no longer active");
        assertEquals(1, cookLogRepository.findAllByPlanEntryOrderByCookedAtDesc(restored).size());

        Recipe refreshed = recipeService.requireOwned(chili.getId(), user);
        assertEquals(0, refreshed.getTimesCooked());
        assertNull(refreshed.getLastCookedAt());
    }

    @Test
    void undoingOneOfTwoCookedMealsKeepsTheRecipeHistoryForTheOther() {
        User user = cook("plan-undo-one@example.com");
        Recipe soup = recipe(user, "Soup", 4);
        MealPlan created = plan(user, "Two dinners");
        PlanEntry first = mealPlanService.addEntry(created.getId(),
                entryFor(soup, MONDAY, MealSlot.DINNER, 4), user);
        PlanEntry second = mealPlanService.addEntry(created.getId(),
                entryFor(soup, MONDAY.plusDays(1), MealSlot.DINNER, 4), user);

        mealPlanService.markEntryCooked(created.getId(), first.getId(), user);
        mealPlanService.markEntryCooked(created.getId(), second.getId(), user);
        mealPlanService.markEntryNotCooked(created.getId(), second.getId(), user);

        assertEquals(1, recipeService.requireOwned(soup.getId(), user).getTimesCooked());
        List<PlanEntry> entries = mealPlanService.entriesInOrder(
                mealPlanService.requireOwned(created.getId(), user));
        assertTrue(entries.getFirst().isCooked());
        assertFalse(entries.get(1).isCooked());
    }

    @Test
    void bulkActionsStayInsideTheSelectedPlan() {
        User user = cook("plan-bulk-actions@example.com");
        Recipe soup = recipe(user, "Soup", 4);
        MealPlan first = plan(user, "First week");
        MealPlanForm nextWeek = new MealPlanForm();
        nextWeek.setName("Second week");
        nextWeek.setWeekStartDate(MONDAY.plusDays(7));
        MealPlan second = mealPlanService.create(nextWeek, user);
        PlanEntry inFirst = mealPlanService.addEntry(first.getId(),
                entryFor(soup, MONDAY, MealSlot.DINNER, 4), user);
        PlanEntry inSecond = mealPlanService.addEntry(second.getId(),
                entryFor(soup, MONDAY.plusDays(7), MealSlot.DINNER, 4), user);

        BulkDeleteResult result = mealPlanService.removeEntries(first.getId(),
                List.of(inFirst.getId(), inSecond.getId()), user);

        assertEquals(1, result.getDeletedCount());
        assertEquals(1, result.getMissingCount());
        assertEquals(1, mealPlanService.entriesInOrder(
                mealPlanService.requireOwned(second.getId(), user)).size());
    }

    @Test
    void cookingNeverDrawsFromExpiredStock() {
        User user = cook("plan-cook-expired@example.com");
        Ingredient milk = ingredient(user, "Milk", Unit.MILLILITER);
        PantryItem expired = stock(user, milk, "500");
        expired.setExpiresOn(LocalDate.now().minusDays(1));
        PantryItem fresh = stock(user, milk, "100");
        Recipe custard = recipeUsing(user, "Custard", 4, milk, "80", Unit.MILLILITER);
        MealPlan created = plan(user, "Expiry-aware week");
        PlanEntry entry = mealPlanService.addEntry(created.getId(),
                entryFor(custard, MONDAY, MealSlot.DINNER, 4), user);

        mealPlanService.markEntryCooked(created.getId(), entry.getId(), user);

        assertEquals(0, new java.math.BigDecimal("500").compareTo(expired.getQuantity()),
                "the expired carton is left alone, even though it expires soonest");
        assertEquals(0, new java.math.BigDecimal("20").compareTo(fresh.getQuantity()),
                "the 80 ml comes out of the carton that is still good");
    }

    @Test
    void anIngredientWithCookingHistoryCannotBeDeleted() {
        User user = cook("plan-cook-history@example.com");
        Ingredient saffron = ingredient(user, "Saffron", Unit.GRAM);
        stock(user, saffron, "1");
        Recipe paella = recipeUsing(user, "Paella", 4, saffron, "1", Unit.GRAM);
        MealPlan created = plan(user, "Cooked with saffron");
        PlanEntry entry = mealPlanService.addEntry(created.getId(),
                entryFor(paella, MONDAY, MealSlot.DINNER, 4), user);
        mealPlanService.markEntryCooked(created.getId(), entry.getId(), user);

        /* Take away every other reference: the cook used up the pantry row,
           and the recipe no longer lists saffron. Only the cook log is left. */
        assertTrue(pantryService.findAll(user).isEmpty());
        RecipeForm withoutSaffron = new RecipeForm();
        withoutSaffron.setId(paella.getId());
        withoutSaffron.setName("Paella");
        withoutSaffron.setServings(4);
        recipeService.update(paella.getId(), withoutSaffron, user);

        IngredientInUseException refused = assertThrows(IngredientInUseException.class,
                () -> ingredientService.delete(saffron.getId(), user));
        assertTrue(refused.getMessage().contains("cooked meal"),
                "the cook sees why, instead of a database error");
    }
}
