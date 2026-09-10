package edu.wgu.pantryplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryplan.domain.MealPlan;
import edu.wgu.pantryplan.domain.MealSlot;
import edu.wgu.pantryplan.domain.PlanEntry;
import edu.wgu.pantryplan.domain.Recipe;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.service.MealPlanService;
import edu.wgu.pantryplan.service.RecipeService;
import edu.wgu.pantryplan.service.UserService;
import edu.wgu.pantryplan.web.form.MealPlanForm;
import edu.wgu.pantryplan.web.form.PlanEntryForm;
import edu.wgu.pantryplan.web.form.RecipeForm;
import edu.wgu.pantryplan.web.form.RegistrationForm;
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
        Recipe stew = recipe(user, "Beef stew", 6);

        mealPlanService.addEntry(created.getId(),
                entryFor(stew, MONDAY.plusDays(1), MealSlot.DINNER, 6), user);
        mealPlanService.addEntry(created.getId(),
                entryFor(eggs, MONDAY, MealSlot.BREAKFAST, 2), user);
        mealPlanService.addEntry(created.getId(),
                entryFor(stew, MONDAY, MealSlot.DINNER, 4), user);

        List<PlanEntry> ordered = mealPlanService.entriesInOrder(
                mealPlanService.requireOwned(created.getId(), user));

        assertEquals(3, ordered.size());
        assertEquals(MONDAY, ordered.get(0).getPlanDate());
        assertEquals(MealSlot.BREAKFAST, ordered.get(0).getMealSlot(),
                "breakfast comes before dinner on the same day");
        assertEquals(MealSlot.DINNER, ordered.get(1).getMealSlot());
        assertEquals(MONDAY.plusDays(1), ordered.get(2).getPlanDate());
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
}
