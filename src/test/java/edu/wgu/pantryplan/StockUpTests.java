package edu.wgu.pantryplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.wgu.pantryplan.domain.GroceryList;
import edu.wgu.pantryplan.domain.GroceryListItem;
import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.IngredientCategory;
import edu.wgu.pantryplan.domain.MealPlan;
import edu.wgu.pantryplan.domain.MealSlot;
import edu.wgu.pantryplan.domain.PantryItem;
import edu.wgu.pantryplan.domain.Recipe;
import edu.wgu.pantryplan.domain.StorageLocation;
import edu.wgu.pantryplan.domain.Unit;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.security.AppUserDetails;
import edu.wgu.pantryplan.service.GroceryListService;
import edu.wgu.pantryplan.service.IngredientService;
import edu.wgu.pantryplan.service.MealPlanService;
import edu.wgu.pantryplan.service.PantryService;
import edu.wgu.pantryplan.service.RecipeService;
import edu.wgu.pantryplan.service.UserService;
import edu.wgu.pantryplan.web.form.IngredientForm;
import edu.wgu.pantryplan.web.form.MealPlanForm;
import edu.wgu.pantryplan.web.form.PlanEntryForm;
import edu.wgu.pantryplan.web.form.RecipeForm;
import edu.wgu.pantryplan.web.form.RecipeLineForm;
import edu.wgu.pantryplan.web.form.RegistrationForm;
import edu.wgu.pantryplan.web.form.StockUpForm;
import edu.wgu.pantryplan.web.form.StockUpRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Putting a shop away: bought lines become pantry entries in one submission.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StockUpTests {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);

    @Autowired private MockMvc mockMvc;
    @Autowired private GroceryListService groceryListService;
    @Autowired private MealPlanService mealPlanService;
    @Autowired private RecipeService recipeService;
    @Autowired private IngredientService ingredientService;
    @Autowired private PantryService pantryService;
    @Autowired private UserService userService;

    /* ------------------------------------------------------------ helpers */

    private User cook(String email) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName("Cook");
        form.setEmail(email);
        form.setPassword("correcthorsebattery");
        form.setConfirmPassword("correcthorsebattery");
        return userService.register(form);
    }

    private Ingredient ingredient(User user, String name, Unit stockUnit, String usualAmount) {
        IngredientForm form = new IngredientForm();
        form.setName(name);
        form.setCategory(IngredientCategory.PANTRY_STAPLE);
        form.setStockUnit(stockUnit);
        form.setDefaultLocation(StorageLocation.PANTRY);
        if (usualAmount != null) {
            form.setDefaultQuantity(new BigDecimal(usualAmount));
        }
        return ingredientService.create(form, user);
    }

    private Recipe recipe(User user, String name, Ingredient ingredient, String quantity, Unit unit) {
        RecipeForm form = new RecipeForm();
        form.setName(name);
        form.setServings(4);
        RecipeLineForm line = new RecipeLineForm();
        line.setIngredientId(ingredient.getId());
        line.setQuantity(new BigDecimal(quantity));
        line.setUnit(unit);
        form.getLines().add(line);
        return recipeService.create(form, user);
    }

    /** A generated list for a week containing the given recipes. */
    private GroceryList listFor(User user, String planName, Recipe... recipes) {
        MealPlanForm planForm = new MealPlanForm();
        planForm.setName(planName);
        planForm.setWeekStartDate(MONDAY);
        MealPlan week = mealPlanService.create(planForm, user);

        int day = 0;
        for (Recipe recipe : recipes) {
            PlanEntryForm entry = new PlanEntryForm();
            entry.setRecipeId(recipe.getId());
            entry.setPlanDate(MONDAY.plusDays(day++));
            entry.setMealSlot(MealSlot.DINNER);
            entry.setServings(4);
            mealPlanService.addEntry(week.getId(), entry, user);
        }
        return groceryListService.generate(week.getId(), user, MONDAY);
    }

    private GroceryListItem itemNamed(GroceryList list, String ingredientName) {
        return list.getItems().stream()
                .filter(item -> item.getIngredient().getName().equals(ingredientName))
                .findFirst().orElseThrow();
    }

    private StockUpRow row(GroceryListItem item, String quantity, StorageLocation location, LocalDate expiresOn) {
        StockUpRow row = new StockUpRow();
        row.setItemId(item.getId());
        row.setQuantity(quantity == null ? null : new BigDecimal(quantity));
        row.setLocation(location);
        row.setExpiresOn(expiresOn);
        return row;
    }

    private StockUpForm formOf(LocalDate purchasedOn, StockUpRow... rows) {
        StockUpForm form = new StockUpForm();
        form.setPurchasedOn(purchasedOn);
        form.setRows(new java.util.ArrayList<>(List.of(rows)));
        return form;
    }

    /* -------------------------------------------------------------- tests */

    @Test
    void addsEveryTickedLineToThePantryInOneGo() {
        User user = cook("stockup-basic@example.com");
        Ingredient flour = ingredient(user, "Flour", Unit.POUND, null);
        Ingredient milk = ingredient(user, "Milk", Unit.CUP, null);
        GroceryList list = listFor(user, "Stock-up week",
                recipe(user, "Pancakes", flour, "1", Unit.POUND),
                recipe(user, "Custard", milk, "2", Unit.CUP));

        GroceryListItem flourItem = itemNamed(list, "Flour");
        GroceryListItem milkItem = itemNamed(list, "Milk");
        groceryListService.togglePurchased(list.getId(), flourItem.getId(), user);
        groceryListService.togglePurchased(list.getId(), milkItem.getId(), user);

        /* A five pound bag covers a list asking for one pound: the amount
           bought is not the amount needed. */
        int stocked = groceryListService.stockUp(list.getId(), formOf(MONDAY,
                row(flourItem, "5", StorageLocation.PANTRY, null),
                row(milkItem, "16", StorageLocation.FRIDGE, MONDAY.plusDays(10))), user);

        assertEquals(2, stocked);
        List<PantryItem> shelf = pantryService.findAll(user);
        assertEquals(2, shelf.size());

        PantryItem storedFlour = shelf.stream()
                .filter(item -> item.getIngredient().getName().equals("Flour")).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("5").compareTo(storedFlour.getQuantity()),
                "the amount bought, not the amount the list asked for");
        assertEquals(StorageLocation.PANTRY, storedFlour.getLocation());
        assertEquals(MONDAY, storedFlour.getPurchasedOn(), "the trip's date is used for every row");
        assertNull(storedFlour.getExpiresOn());

        PantryItem storedMilk = shelf.stream()
                .filter(item -> item.getIngredient().getName().equals("Milk")).findFirst().orElseThrow();
        assertEquals(StorageLocation.FRIDGE, storedMilk.getLocation());
        assertEquals(MONDAY.plusDays(10), storedMilk.getExpiresOn());
    }

    @Test
    void onlyOffersLinesThatWereBoughtAndNotYetPutAway() {
        User user = cook("stockup-ready@example.com");
        Ingredient rice = ingredient(user, "Rice", Unit.POUND, null);
        Ingredient oats = ingredient(user, "Oats", Unit.POUND, null);
        GroceryList list = listFor(user, "Ready week",
                recipe(user, "Pilaf", rice, "1", Unit.POUND),
                recipe(user, "Porridge", oats, "1", Unit.POUND));

        assertTrue(groceryListService.readyToStock(list).isEmpty(), "nothing is bought yet");

        GroceryListItem riceItem = itemNamed(list, "Rice");
        groceryListService.togglePurchased(list.getId(), riceItem.getId(), user);
        assertEquals(List.of("Rice"), groceryListService.readyToStock(list).stream()
                .map(item -> item.getIngredient().getName()).toList());

        groceryListService.stockUp(list.getId(),
                formOf(MONDAY, row(riceItem, "2", StorageLocation.PANTRY, null)), user);

        assertTrue(groceryListService.readyToStock(list).isEmpty(),
                "a line that has been put away is not offered again");
        assertTrue(riceItem.isStocked());
    }

    @Test
    void refusesToPutTheSameLineAwayTwice() {
        User user = cook("stockup-twice@example.com");
        Ingredient sugar = ingredient(user, "Sugar", Unit.POUND, null);
        GroceryList list = listFor(user, "Twice week", recipe(user, "Cake", sugar, "1", Unit.POUND));
        GroceryListItem item = itemNamed(list, "Sugar");
        groceryListService.togglePurchased(list.getId(), item.getId(), user);

        StockUpForm form = formOf(MONDAY, row(item, "4", StorageLocation.PANTRY, null));
        groceryListService.stockUp(list.getId(), form, user);

        /* Resubmitting the same dialog, for instance with the back button,
           must not double the pantry. */
        assertThrows(NoSuchElementException.class,
                () -> groceryListService.stockUp(list.getId(), form, user));
        assertEquals(1, pantryService.findAll(user).size());
    }

    @Test
    void skipsRowsTheCookUnticked() {
        User user = cook("stockup-skip@example.com");
        Ingredient salt = ingredient(user, "Salt", Unit.OUNCE, null);
        Ingredient pepper = ingredient(user, "Pepper", Unit.OUNCE, null);
        GroceryList list = listFor(user, "Skip week",
                recipe(user, "Seasoned rice", salt, "1", Unit.OUNCE),
                recipe(user, "Steak", pepper, "1", Unit.OUNCE));
        GroceryListItem saltItem = itemNamed(list, "Salt");
        GroceryListItem pepperItem = itemNamed(list, "Pepper");
        groceryListService.togglePurchased(list.getId(), saltItem.getId(), user);
        groceryListService.togglePurchased(list.getId(), pepperItem.getId(), user);

        /* What the browser sends for a line the cook unticked. */
        StockUpRow skipped = row(pepperItem, null, StorageLocation.SPICE_RACK, null);
        skipped.setInclude(false);
        int stocked = groceryListService.stockUp(list.getId(),
                formOf(MONDAY, row(saltItem, "26", StorageLocation.PANTRY, null), skipped), user);

        assertEquals(1, stocked);
        assertEquals(1, pantryService.findAll(user).size());
        assertFalse(pepperItem.isStocked(), "an unticked line stays waiting");
        assertEquals(List.of("Pepper"), groceryListService.readyToStock(list).stream()
                .map(item -> item.getIngredient().getName()).toList());
    }

    @Test
    void refusesALineFromAnotherAccountsList() {
        User owner = cook("stockup-owner@example.com");
        User stranger = cook("stockup-stranger@example.com");
        Ingredient beans = ingredient(owner, "Beans", Unit.OUNCE, null);
        GroceryList list = listFor(owner, "Owner week", recipe(owner, "Chili", beans, "8", Unit.OUNCE));
        GroceryListItem item = itemNamed(list, "Beans");
        groceryListService.togglePurchased(list.getId(), item.getId(), owner);

        assertThrows(NoSuchElementException.class, () -> groceryListService.stockUp(list.getId(),
                formOf(MONDAY, row(item, "15", StorageLocation.PANTRY, null)), stranger));
        assertTrue(pantryService.findAll(stranger).isEmpty());
    }

    /* ---------------------------------------------------------- the page */

    @Test
    void theDialogPrefillsTheUsualAmountAndUsualShelf() throws Exception {
        User user = cook("stockup-prefill@example.com");
        Ingredient flour = ingredient(user, "Flour", Unit.POUND, "5");
        GroceryList list = listFor(user, "Prefill week", recipe(user, "Bread", flour, "1", Unit.POUND));
        GroceryListItem item = itemNamed(list, "Flour");
        groceryListService.togglePurchased(list.getId(), item.getId(), user);

        String page = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/grocery-lists/{id}", list.getId())
                        .with(user(new AppUserDetails(user))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(page.contains("Put 1 bought item away"), "the button offers the waiting line");
        assertTrue(page.contains("value=\"5\""), "the usual amount is offered, not the 1 lb shortfall");
        assertTrue(page.contains("List asked for"), "the list's amount is still shown for reference");
    }

    /**
     * The dialog holds a row for every line that has not been put away, hidden
     * until its line is ticked, which is what lets the browser show and hide
     * them without fetching the page again.
     */
    @Test
    void theDialogCarriesRowsForUntickedLinesToo() throws Exception {
        User user = cook("stockup-rows@example.com");
        Ingredient jam = ingredient(user, "Jam", Unit.OUNCE, null);
        Ingredient tea = ingredient(user, "Tea", Unit.OUNCE, null);
        GroceryList list = listFor(user, "Rows week",
                recipe(user, "Toast", jam, "2", Unit.OUNCE),
                recipe(user, "Brew", tea, "1", Unit.OUNCE));
        groceryListService.togglePurchased(list.getId(), itemNamed(list, "Jam").getId(), user);

        String page = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/grocery-lists/{id}", list.getId())
                        .with(user(new AppUserDetails(user))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(page.contains("data-stockup-row=\"" + itemNamed(list, "Jam").getId() + "\""));
        assertTrue(page.contains("data-stockup-row=\"" + itemNamed(list, "Tea").getId() + "\""),
                "the unticked line has a row waiting, hidden");
        assertEquals(1, groceryListService.readyToStock(list).size(), "but only one is ready to stock");
        assertEquals(2, groceryListService.stockable(list).size());
    }

    @Test
    void aMissingAmountReopensTheDialogWithoutTouchingThePantry() throws Exception {
        User user = cook("stockup-invalid@example.com");
        Ingredient oil = ingredient(user, "Oil", Unit.CUP, null);
        GroceryList list = listFor(user, "Invalid week", recipe(user, "Fries", oil, "2", Unit.CUP));
        GroceryListItem item = itemNamed(list, "Oil");
        groceryListService.togglePurchased(list.getId(), item.getId(), user);

        String page = mockMvc.perform(post("/grocery-lists/{id}/stock-up", list.getId())
                        .with(user(new AppUserDetails(user))).with(csrf())
                        .param("purchasedOn", MONDAY.toString())
                        .param("rows[0].itemId", item.getId().toString())
                        .param("rows[0].include", "true")
                        .param("rows[0].quantity", "")
                        .param("rows[0].location", "PANTRY"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(page.contains("Enter how much you bought"));
        assertTrue(page.contains("data-open-dialog=\"stockup-dialog\""), "the dialog reopens with the error");
        assertTrue(pantryService.findAll(user).isEmpty(), "nothing reached the pantry");
        assertFalse(item.isStocked());
    }

    @Test
    void anExpiryBeforeTheShoppingDateIsRejected() throws Exception {
        User user = cook("stockup-backwards@example.com");
        Ingredient cream = ingredient(user, "Cream", Unit.CUP, null);
        GroceryList list = listFor(user, "Backwards week", recipe(user, "Sauce", cream, "1", Unit.CUP));
        GroceryListItem item = itemNamed(list, "Cream");
        groceryListService.togglePurchased(list.getId(), item.getId(), user);

        String page = mockMvc.perform(post("/grocery-lists/{id}/stock-up", list.getId())
                        .with(user(new AppUserDetails(user))).with(csrf())
                        .param("purchasedOn", MONDAY.toString())
                        .param("rows[0].itemId", item.getId().toString())
                        .param("rows[0].include", "true")
                        .param("rows[0].quantity", "2")
                        .param("rows[0].location", "FRIDGE")
                        .param("rows[0].expiresOn", MONDAY.minusDays(1).toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(page.contains("That is before the shopping date"));
        assertTrue(pantryService.findAll(user).isEmpty());
    }

    @Test
    void aValidSubmissionRedirectsBackToTheList() throws Exception {
        User user = cook("stockup-post@example.com");
        Ingredient pasta = ingredient(user, "Pasta", Unit.OUNCE, null);
        GroceryList list = listFor(user, "Post week", recipe(user, "Spaghetti", pasta, "8", Unit.OUNCE));
        GroceryListItem item = itemNamed(list, "Pasta");
        groceryListService.togglePurchased(list.getId(), item.getId(), user);

        mockMvc.perform(post("/grocery-lists/{id}/stock-up", list.getId())
                        .with(user(new AppUserDetails(user))).with(csrf())
                        .param("purchasedOn", MONDAY.toString())
                        .param("rows[0].itemId", item.getId().toString())
                        .param("rows[0].include", "true")
                        .param("rows[0].quantity", "16")
                        .param("rows[0].location", "PANTRY"))
                .andExpect(redirectedUrl("/grocery-lists/" + list.getId()));

        assertEquals(1, pantryService.findAll(user).size());
    }

    /* ------------------------------------------------------------- undoing */

    @Test
    void undoingTakesTheShelfEntryBackOff() {
        User user = cook("stockup-undo@example.com");
        Ingredient sugar = ingredient(user, "Sugar", Unit.POUND, null);
        GroceryList list = listFor(user, "Undo week", recipe(user, "Cake", sugar, "1", Unit.POUND));
        GroceryListItem item = itemNamed(list, "Sugar");
        groceryListService.togglePurchased(list.getId(), item.getId(), user);
        groceryListService.stockUp(list.getId(),
                formOf(MONDAY, row(item, "4", StorageLocation.PANTRY, null)), user);

        assertEquals(0, new BigDecimal("4").compareTo(item.getStockedQuantity()),
                "the list remembers what it put away");
        assertEquals(1, pantryService.findAll(user).size());

        assertTrue(groceryListService.undoStock(list.getId(), item.getId(), user));

        assertTrue(pantryService.findAll(user).isEmpty(), "the shelf entry is gone");
        assertFalse(item.isStocked());
        assertNull(item.getStockedQuantity());
        assertEquals(List.of("Sugar"), groceryListService.readyToStock(list).stream()
                .map(waiting -> waiting.getIngredient().getName()).toList());
    }

    @Test
    void aLineCanBePutAwayAgainAfterUndoing() {
        User user = cook("stockup-redo@example.com");
        Ingredient rice = ingredient(user, "Rice", Unit.POUND, null);
        GroceryList list = listFor(user, "Redo week", recipe(user, "Pilaf", rice, "1", Unit.POUND));
        GroceryListItem item = itemNamed(list, "Rice");
        groceryListService.togglePurchased(list.getId(), item.getId(), user);

        groceryListService.stockUp(list.getId(),
                formOf(MONDAY, row(item, "2", StorageLocation.PANTRY, null)), user);
        groceryListService.undoStock(list.getId(), item.getId(), user);
        groceryListService.stockUp(list.getId(),
                formOf(MONDAY, row(item, "5", StorageLocation.FRIDGE, null)), user);

        List<PantryItem> shelf = pantryService.findAll(user);
        assertEquals(1, shelf.size(), "one entry, not two");
        assertEquals(0, new BigDecimal("5").compareTo(shelf.get(0).getQuantity()),
                "the corrected amount, not the first one");
    }

    /**
     * The pantry row can be cleared off the pantry page on its own. The link
     * goes with it, and undoing then only has the list left to tidy.
     */
    @Test
    void undoingStillWorksWhenTheShelfEntryIsAlreadyGone() {
        User user = cook("stockup-undo-gone@example.com");
        Ingredient tea = ingredient(user, "Tea", Unit.OUNCE, null);
        GroceryList list = listFor(user, "Gone week", recipe(user, "Brew", tea, "1", Unit.OUNCE));
        GroceryListItem item = itemNamed(list, "Tea");
        groceryListService.togglePurchased(list.getId(), item.getId(), user);
        groceryListService.stockUp(list.getId(),
                formOf(MONDAY, row(item, "8", StorageLocation.PANTRY, null)), user);

        pantryService.delete(pantryService.findAll(user).get(0).getId(), user);

        assertFalse(groceryListService.undoStock(list.getId(), item.getId(), user),
                "there was no shelf entry left to remove");
        assertFalse(item.isStocked(), "but the line is ready to put away again");
    }

    @Test
    void onlyAStockedLineCanBeUndoneAndOnlyByItsOwner() {
        User owner = cook("stockup-undo-owner@example.com");
        User stranger = cook("stockup-undo-stranger@example.com");
        Ingredient jam = ingredient(owner, "Jam", Unit.OUNCE, null);
        GroceryList list = listFor(owner, "Owner undo week", recipe(owner, "Toast", jam, "2", Unit.OUNCE));
        GroceryListItem item = itemNamed(list, "Jam");

        assertThrows(NoSuchElementException.class,
                () -> groceryListService.undoStock(list.getId(), item.getId(), owner),
                "nothing was put away yet");

        groceryListService.togglePurchased(list.getId(), item.getId(), owner);
        groceryListService.stockUp(list.getId(),
                formOf(MONDAY, row(item, "12", StorageLocation.PANTRY, null)), owner);

        assertThrows(NoSuchElementException.class,
                () -> groceryListService.undoStock(list.getId(), item.getId(), stranger));
        assertEquals(1, pantryService.findAll(owner).size(), "the owner's shelf is untouched");
    }

    @Test
    void theListShowsWhatWasStockedWithAWayBack() throws Exception {
        User user = cook("stockup-shown@example.com");
        Ingredient flour = ingredient(user, "Flour", Unit.POUND, null);
        GroceryList list = listFor(user, "Shown week", recipe(user, "Bread", flour, "1", Unit.POUND));
        GroceryListItem item = itemNamed(list, "Flour");
        groceryListService.togglePurchased(list.getId(), item.getId(), user);
        groceryListService.stockUp(list.getId(),
                formOf(MONDAY, row(item, "5", StorageLocation.PANTRY, null)), user);

        String page = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/grocery-lists/{id}", list.getId())
                        .with(user(new AppUserDetails(user))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(page.contains("5 lb"), "the amount that went on the shelf is shown");
        assertTrue(page.contains("/items/" + item.getId() + "/unstock"), "with a way to undo it");
        assertFalse(page.contains("Put 1 bought item away"),
                "and it is no longer offered in the stock-up dialog");
    }
}
