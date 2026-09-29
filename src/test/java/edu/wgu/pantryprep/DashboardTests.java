package edu.wgu.pantryprep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.wgu.pantryprep.domain.GroceryList;
import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.IngredientCategory;
import edu.wgu.pantryprep.domain.MealPlan;
import edu.wgu.pantryprep.domain.MealSlot;
import edu.wgu.pantryprep.domain.PlanEntry;
import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.StorageLocation;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.DashboardService;
import edu.wgu.pantryprep.service.GroceryListService;
import edu.wgu.pantryprep.service.IngredientService;
import edu.wgu.pantryprep.service.MealPlanService;
import edu.wgu.pantryprep.service.PantryService;
import edu.wgu.pantryprep.service.RecipeService;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.IngredientForm;
import edu.wgu.pantryprep.web.form.MealPlanForm;
import edu.wgu.pantryprep.web.form.PantryItemForm;
import edu.wgu.pantryprep.web.form.PlanEntryForm;
import edu.wgu.pantryprep.web.form.RecipeForm;
import edu.wgu.pantryprep.web.form.RegistrationForm;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The home page: a checklist for a new account, and the dashboard once there
 * is something to plan around.
 *
 * <p>The dashboard reads with plain SQL, which does not trigger Hibernate's
 * automatic flush, so each test flushes before it reads.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardTests {

    /** A Wednesday. The plan below starts on the Monday before it. */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 16);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DashboardService dashboard;

    @Autowired
    private UserService userService;

    @Autowired
    private IngredientService ingredientService;

    @Autowired
    private PantryService pantryService;

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private MealPlanService mealPlanService;

    @Autowired
    private GroceryListService groceryListService;

    @PersistenceContext
    private EntityManager entityManager;

    private User cook(String email) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName("Sam Cook");
        form.setEmail(email);
        form.setPassword("correcthorsebattery");
        form.setConfirmPassword("correcthorsebattery");
        return userService.register(form);
    }

    private Ingredient ingredient(User user, String name) {
        IngredientForm form = new IngredientForm();
        form.setName(name);
        form.setCategory(IngredientCategory.PRODUCE);
        form.setStockUnit(Unit.GRAM);
        form.setDefaultLocation(StorageLocation.FRIDGE);
        return ingredientService.create(form, user);
    }

    private void stock(User user, String name, LocalDate expiresOn) {
        PantryItemForm form = new PantryItemForm();
        form.setIngredientId(ingredient(user, name).getId());
        form.setQuantity(new BigDecimal("200"));
        form.setLocation(StorageLocation.FRIDGE);
        form.setExpiresOn(expiresOn);
        pantryService.create(form, user);
    }

    private Recipe recipe(User user, String name) {
        RecipeForm form = new RecipeForm();
        form.setName(name);
        form.setServings(4);
        return recipeService.create(form, user);
    }

    private MealPlan plan(User user, LocalDate weekStart) {
        MealPlanForm form = new MealPlanForm();
        form.setName("Plan from " + weekStart);
        form.setWeekStartDate(weekStart);
        return mealPlanService.create(form, user);
    }

    private PlanEntry meal(User user, MealPlan plan, Recipe recipe, LocalDate date, MealSlot slot) {
        PlanEntryForm form = new PlanEntryForm();
        form.setRecipeId(recipe.getId());
        form.setPlanDate(date);
        form.setMealSlot(slot);
        form.setServings(2);
        return mealPlanService.addEntry(plan.getId(), form, user);
    }

    @Test
    void aNewAccountIsNewUntilItHasSomethingToPlanAround() {
        User user = cook("dash-new@example.com");
        entityManager.flush();

        DashboardService.Counts empty = dashboard.counts(user.getId());
        assertTrue(empty.isNew());
        assertFalse(empty.setupComplete());

        ingredient(user, "Salt");
        entityManager.flush();
        assertTrue(dashboard.counts(user.getId()).isNew(), "ingredients alone give nothing to plan");

        recipe(user, "Toast");
        stock(user, "Bread", null);
        plan(user, TODAY);
        entityManager.flush();
        DashboardService.Counts started = dashboard.counts(user.getId());
        assertFalse(started.isNew());
        assertTrue(started.setupComplete());
    }

    @Test
    void useItUpListsExpiredAndSoonToExpireFirstAndNothingElse() {
        User user = cook("dash-expiry@example.com");
        User other = cook("dash-expiry-other@example.com");
        stock(user, "Spinach", TODAY.plusDays(2));
        stock(user, "Old milk", TODAY.minusDays(1));
        stock(user, "Rice", TODAY.plusDays(30));
        stock(user, "Salt", null);
        stock(other, "Their yogurt", TODAY);
        entityManager.flush();

        List<DashboardService.Expiring> items = dashboard.expiring(user.getId(), TODAY, 7, 10);

        assertEquals(List.of("Old milk", "Spinach"), items.stream().map(DashboardService.Expiring::name).toList());
        assertTrue(items.get(0).expired());
        assertEquals("Expired yesterday", items.get(0).when());
        assertEquals("Expires in 2 days", items.get(1).when());

        DashboardService.ExpiryCounts counts = dashboard.expiryCounts(user.getId(), TODAY, 7);
        assertEquals(1, counts.expired());
        assertEquals(1, counts.soon());
    }

    @Test
    void thisWeekIsThePlanCoveringTodayWithBreakfastBeforeDinner() {
        User user = cook("dash-week@example.com");
        Recipe soup = recipe(user, "Lentil soup");
        Recipe oats = recipe(user, "Overnight oats");
        LocalDate monday = TODAY.minusDays(2);
        MealPlan thisWeek = plan(user, monday);
        plan(user, TODAY.plusDays(5));
        meal(user, thisWeek, soup, TODAY, MealSlot.DINNER);
        PlanEntry breakfast = meal(user, thisWeek, oats, TODAY, MealSlot.BREAKFAST);
        meal(user, thisWeek, soup, monday, MealSlot.DINNER);
        entityManager.flush();

        DashboardService.WeekPlan week = dashboard.weekPlan(user.getId(), TODAY);

        assertEquals(thisWeek.getId(), week.id());
        assertEquals(7, week.days().size());
        DashboardService.PlanDay today = week.days().get(2);
        assertTrue(today.isToday());
        assertEquals("Today", today.label());
        assertEquals(List.of("Overnight oats", "Lentil soup"),
                today.meals().stream().map(DashboardService.PlannedMeal::recipeName).toList());
        assertTrue(week.days().get(0).isPast());
        assertEquals(2, week.mealsLeft(), "Monday's meal is in the past and not counted");
        assertEquals(3, week.mealsPlanned());
        assertNull(week.groceryListId());

        mealPlanService.markEntryCooked(thisWeek.getId(), breakfast.getId(), user);
        GroceryList list = groceryListService.generate(thisWeek.getId(), user, TODAY);
        entityManager.flush();

        DashboardService.WeekPlan after = dashboard.weekPlan(user.getId(), TODAY);
        assertEquals(1, after.mealsLeft());
        assertEquals(list.getId(), after.groceryListId());
        assertEquals(list.getId(), dashboard.latestList(user.getId()).id());
        assertEquals("Overnight oats", dashboard.recentlyCooked(user.getId(), 5).getFirst().recipeName());
    }

    @Test
    void noPlanCoversAWeekWithoutOne() {
        User user = cook("dash-noplan@example.com");
        plan(user, TODAY.plusDays(10));
        entityManager.flush();

        assertNull(dashboard.weekPlan(user.getId(), TODAY));
        assertNull(dashboard.latestList(user.getId()));
    }

    @Test
    void aNewAccountSeesTheGettingStartedChecklist() throws Exception {
        User user = cook("dash-page-new@example.com");
        entityManager.flush();

        String page = mockMvc.perform(get("/dashboard").with(user(new AppUserDetails(user))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(page.contains("Welcome, Sam"));
        assertTrue(page.contains("Get started"));
        assertTrue(page.contains("Add your ingredients"));
        assertFalse(page.contains("home-tiles"), "no dashboard until there is something on it");
    }

    @Test
    void anActiveAccountSeesWhatToUseAndThisWeeksMeals() throws Exception {
        LocalDate now = LocalDate.now();
        User user = cook("dash-page-active@example.com");
        stock(user, "Spinach", now.plusDays(1));
        Recipe soup = recipe(user, "Lentil soup");
        MealPlan week = plan(user, now);
        meal(user, week, soup, now, MealSlot.DINNER);
        entityManager.flush();

        String page = mockMvc.perform(get("/dashboard").with(user(new AppUserDetails(user))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertFalse(page.contains("Get started"));
        assertTrue(page.contains("home-tiles"));
        assertTrue(page.contains("Spinach"));
        assertTrue(page.contains("Expires tomorrow"));
        assertTrue(page.contains("Lentil soup"));
        assertTrue(page.contains("No grocery list for this plan yet"));
    }
}
