package edu.wgu.pantryprep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.GroceryListService;
import edu.wgu.pantryprep.service.IngredientService;
import edu.wgu.pantryprep.service.MealPlanService;
import edu.wgu.pantryprep.service.RecipeService;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.IngredientForm;
import edu.wgu.pantryprep.web.form.MealPlanForm;
import edu.wgu.pantryprep.web.form.PlanEntryForm;
import edu.wgu.pantryprep.web.form.RecipeForm;
import edu.wgu.pantryprep.web.form.RecipeLineForm;
import edu.wgu.pantryprep.web.form.RegistrationForm;
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
 * Renders the grocery pages through the full web stack.
 *
 * <p>Service tests never touch a template, so a broken Thymeleaf expression
 * passes every one of them and only fails in the browser. These requests run
 * the controller, the security filters, and the template together.
 *
 * <p>One limit: the test transaction keeps the persistence context open, so a
 * missing lazy load that would fail with open-in-view off still renders here.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GroceryListPageTests {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GroceryListService groceryListService;

    @Autowired
    private MealPlanService mealPlanService;

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private IngredientService ingredientService;

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

    private Ingredient ingredient(User owner, String name, Unit stockUnit) {
        IngredientForm form = new IngredientForm();
        form.setName(name);
        form.setCategory(IngredientCategory.PRODUCE);
        form.setStockUnit(stockUnit);
        form.setDefaultLocation(StorageLocation.FRIDGE);
        return ingredientService.create(form, owner);
    }

    private Recipe recipe(User owner, String name, Ingredient ingredient, String quantity, Unit unit) {
        RecipeForm form = new RecipeForm();
        form.setName(name);
        form.setServings(4);
        RecipeLineForm line = new RecipeLineForm();
        line.setIngredientId(ingredient.getId());
        line.setQuantity(new BigDecimal(quantity));
        line.setUnit(unit);
        form.getLines().add(line);
        return recipeService.create(form, owner);
    }

    private MealPlan planWith(User owner, String name, Recipe... recipes) {
        MealPlanForm planForm = new MealPlanForm();
        planForm.setName(name);
        planForm.setWeekStartDate(MONDAY);
        MealPlan week = mealPlanService.create(planForm, owner);

        for (Recipe recipe : recipes) {
            PlanEntryForm entry = new PlanEntryForm();
            entry.setRecipeId(recipe.getId());
            entry.setPlanDate(MONDAY);
            entry.setMealSlot(MealSlot.DINNER);
            entry.setServings(4);
            mealPlanService.addEntry(week.getId(), entry, owner);
        }
        return week;
    }

    /** A week with one normal item and one flagged item, so both branches render. */
    private MealPlan plannedWeek(User owner) {
        Ingredient basil = ingredient(owner, "Basil", Unit.GRAM);
        Recipe pesto = recipe(owner, "Pesto", basil, "2", Unit.CUP);
        Recipe salad = recipe(owner, "Salad", basil, "50", Unit.GRAM);
        return planWith(owner, "Page week", pesto, salad);
    }

    @Test
    void rendersTheListAndDetailPages() throws Exception {
        User owner = cook("page-render@example.com");
        MealPlan week = plannedWeek(owner);
        GroceryList list = groceryListService.generate(week.getId(), owner, MONDAY);
        AppUserDetails principal = new AppUserDetails(owner);

        String index = mockMvc.perform(get("/grocery-lists").with(user(principal)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(index.contains("Groceries for Page week"), "the list page shows the list");

        String detail = mockMvc.perform(get("/grocery-lists/{id}", list.getId()).with(user(principal)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(detail.contains("Basil"), "the detail page shows the item");
        assertTrue(detail.contains("Check pantry"), "the flagged row is marked");
        assertTrue(detail.contains("weight per cup"), "the flagged row explains why");
    }

    @Test
    void thePlanPageOffersToBuildAListAndThenLinksToIt() throws Exception {
        User owner = cook("page-plan@example.com");
        MealPlan week = plannedWeek(owner);
        AppUserDetails principal = new AppUserDetails(owner);

        String before = mockMvc.perform(get("/meal-plans/{id}", week.getId()).with(user(principal)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(before.contains("Build grocery list"));
        assertTrue(before.contains("entry-bulk-form"), "the plan page renders meal selection controls");
        assertTrue(before.contains("form=\"entry-bulk-form\""),
                "selected meal deletion submits directly through the bulk form");
        assertTrue(before.contains("Mark selected cooked"), "the bulk cook action is available");
        assertTrue(before.contains("Mark cooked"), "each uncooked meal has its own cook action");

        GroceryList list = groceryListService.generate(week.getId(), owner, MONDAY);

        String after = mockMvc.perform(get("/meal-plans/{id}", week.getId()).with(user(principal)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(after.contains("/grocery-lists/" + list.getId()), "the plan links to its list");
    }

    @Test
    void bulkDeletingMealsOnlyRemovesTheSelectedPlanEntries() throws Exception {
        User owner = cook("page-bulk-delete@example.com");
        MealPlan week = plannedWeek(owner);
        List<PlanEntry> scheduled = mealPlanService.entriesInOrder(
                mealPlanService.requireOwned(week.getId(), owner));
        PlanEntry selected = scheduled.getFirst();
        Long recipeId = selected.getRecipe().getId();

        mockMvc.perform(post("/meal-plans/{id}/entries/bulk-delete", week.getId())
                        .param("ids", selected.getId().toString())
                        .with(user(new AppUserDetails(owner)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/meal-plans/" + week.getId()));

        assertEquals(1, mealPlanService.entriesInOrder(
                mealPlanService.requireOwned(week.getId(), owner)).size());
        assertEquals(0, recipeService.requireOwned(recipeId, owner).getTimesCooked(),
                "deleting a scheduled entry never marks or cooks its recipe");
    }

    @Test
    void aCookedMealCanBeUndoneFromThePlanPage() throws Exception {
        User owner = cook("page-undo-cook@example.com");
        MealPlan week = plannedWeek(owner);
        PlanEntry entry = mealPlanService.entriesInOrder(
                mealPlanService.requireOwned(week.getId(), owner)).getFirst();
        AppUserDetails principal = new AppUserDetails(owner);

        mockMvc.perform(post("/meal-plans/{id}/entries/{entryId}/cook", week.getId(), entry.getId())
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/meal-plans/" + week.getId()));

        String cookedPage = mockMvc.perform(get("/meal-plans/{id}", week.getId()).with(user(principal)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(cookedPage.contains("Undo cooked"),
                "each cooked meal exposes its own reversal action");
        assertTrue(cookedPage.contains("Undo selected cooking"),
                "selected meals can also be reversed together");

        mockMvc.perform(post("/meal-plans/{id}/entries/{entryId}/uncook", week.getId(), entry.getId())
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/meal-plans/" + week.getId()));

        PlanEntry restored = mealPlanService.entriesInOrder(
                mealPlanService.requireOwned(week.getId(), owner)).stream()
                .filter(candidate -> candidate.getId().equals(entry.getId()))
                .findFirst().orElseThrow();
        assertFalse(restored.isCooked());
    }

    /**
     * The page ticks in the background and updates itself, so it asks for an
     * empty answer rather than a rendered page it would only discard.
     */
    @Test
    void tickingFromTheBrowserGetsAnEmptyAnswer() throws Exception {
        User owner = cook("page-tick-fetch@example.com");
        MealPlan week = plannedWeek(owner);
        GroceryList list = groceryListService.generate(week.getId(), owner, MONDAY);
        GroceryListItem item = list.getItems().get(0);

        mockMvc.perform(post("/grocery-lists/{id}/items/{itemId}/toggle", list.getId(), item.getId())
                        .with(user(new AppUserDetails(owner)))
                        .with(csrf())
                        .header("X-Requested-With", "fetch"))
                .andExpect(status().isNoContent());

        assertTrue(item.isPurchased(), "the tick was saved");
    }

    /** Without JavaScript the form posts normally and the browser comes back. */
    @Test
    void tickingWithoutJavaScriptRedirectsToTheList() throws Exception {
        User owner = cook("page-tick@example.com");
        MealPlan week = plannedWeek(owner);
        GroceryList list = groceryListService.generate(week.getId(), owner, MONDAY);
        GroceryListItem item = list.getItems().get(0);

        mockMvc.perform(post("/grocery-lists/{id}/items/{itemId}/toggle", list.getId(), item.getId())
                        .with(user(new AppUserDetails(owner)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                /* No fragment: it would scroll the ticked row to the top. */
                .andExpect(redirectedUrl("/grocery-lists/" + list.getId()));

        assertTrue(item.isPurchased(), "the tick was saved");
    }

    @Test
    void thePrintSheetLeavesOffWhatIsAlreadyBought() throws Exception {
        User owner = cook("page-print@example.com");
        Ingredient lemons = ingredient(owner, "Lemons", Unit.PIECE);
        Ingredient limes = ingredient(owner, "Limes", Unit.PIECE);
        MealPlan week = planWith(owner, "Print week",
                recipe(owner, "Lemonade", lemons, "6", Unit.PIECE),
                recipe(owner, "Limeade", limes, "6", Unit.PIECE));
        GroceryList list = groceryListService.generate(week.getId(), owner, MONDAY);

        GroceryListItem lemonItem = list.getItems().stream()
                .filter(item -> item.getIngredient().getName().equals("Lemons"))
                .findFirst().orElseThrow();
        groceryListService.togglePurchased(list.getId(), lemonItem.getId(), owner);

        String sheet = mockMvc.perform(get("/grocery-lists/{id}/print", list.getId())
                        .with(user(new AppUserDetails(owner))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(sheet.contains("Limes"), "what is still to buy is printed");
        assertFalse(sheet.contains("Lemons"), "what is already bought is left off");
        assertTrue(sheet.contains("1 already bought"), "the sheet says something was left off");
    }
}
