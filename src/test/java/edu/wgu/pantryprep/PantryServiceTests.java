package edu.wgu.pantryprep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.IngredientCategory;
import edu.wgu.pantryprep.domain.PantryItem;
import edu.wgu.pantryprep.domain.StorageLocation;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.service.IngredientService;
import edu.wgu.pantryprep.service.PantryService;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.IngredientForm;
import edu.wgu.pantryprep.web.form.PantryItemForm;
import edu.wgu.pantryprep.web.form.RegistrationForm;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PantryServiceTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);

    @Autowired
    private PantryService pantryService;

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

    private Ingredient ingredient(User user, String name, Unit stockUnit) {
        IngredientForm form = new IngredientForm();
        form.setName(name);
        form.setCategory(IngredientCategory.OTHER);
        form.setStockUnit(stockUnit);
        form.setDefaultLocation(StorageLocation.PANTRY);
        return ingredientService.create(form, user);
    }

    private PantryItemForm formFor(Ingredient ingredient, String quantity,
                                   StorageLocation location) {
        PantryItemForm form = new PantryItemForm();
        form.setIngredientId(ingredient.getId());
        form.setQuantity(new BigDecimal(quantity));
        form.setLocation(location);
        return form;
    }

    @Test
    void createsAndListsItemsSortedByIngredientName() {
        User user = cook("pan-create@example.com");
        pantryService.create(formFor(ingredient(user, "Rice", Unit.GRAM), "500.000", StorageLocation.PANTRY), user);
        pantryService.create(formFor(ingredient(user, "Butter", Unit.GRAM), "225.000", StorageLocation.FRIDGE), user);

        var all = pantryService.findAll(user);
        assertEquals(2, all.size());
        assertEquals("Butter", all.get(0).getIngredient().getName());
    }

    @Test
    void keepsItemsScopedToTheOwningAccount() {
        User mine = cook("pan-mine@example.com");
        User theirs = cook("pan-theirs@example.com");
        PantryItem item = pantryService.create(
                formFor(ingredient(mine, "Saffron", Unit.GRAM), "2.000", StorageLocation.SPICE_RACK), mine);

        assertTrue(pantryService.findAll(theirs).isEmpty());
        assertThrows(NoSuchElementException.class,
                () -> pantryService.requireOwned(item.getId(), theirs));
    }

    @Test
    void searchCombinesTermAndLocation() {
        User user = cook("pan-search@example.com");
        pantryService.create(formFor(ingredient(user, "Cheddar cheese", Unit.GRAM), "200.000", StorageLocation.FRIDGE), user);
        pantryService.create(formFor(ingredient(user, "Cream cheese", Unit.GRAM), "150.000", StorageLocation.FRIDGE), user);
        pantryService.create(formFor(ingredient(user, "Cheese crackers", Unit.PIECE), "1.000", StorageLocation.PANTRY), user);

        assertEquals(3, pantryService.search(user, "cheese", null).size());
        assertEquals(2, pantryService.search(user, "cheese", StorageLocation.FRIDGE).size());
        assertEquals(1, pantryService.search(user, null, StorageLocation.PANTRY).size());
        assertEquals(3, pantryService.search(user, "  ", null).size(),
                "a blank search returns everything");
    }

    @Test
    void findsItemsExpiringWithinAWindow() {
        User user = cook("pan-expiry@example.com");

        PantryItemForm soon = formFor(ingredient(user, "Milk", Unit.LITER), "1.000", StorageLocation.FRIDGE);
        soon.setExpiresOn(TODAY.plusDays(3));
        pantryService.create(soon, user);

        PantryItemForm later = formFor(ingredient(user, "Flour", Unit.KILOGRAM), "1.000", StorageLocation.PANTRY);
        later.setExpiresOn(TODAY.plusDays(60));
        pantryService.create(later, user);

        PantryItemForm never = formFor(ingredient(user, "Salt", Unit.GRAM), "500.000", StorageLocation.PANTRY);
        pantryService.create(never, user);

        assertEquals(1, pantryService.findExpiringWithin(user, 7, TODAY).size());
        assertEquals(2, pantryService.findExpiringWithin(user, 90, TODAY).size(),
                "items with no expiry date are never reported as expiring");
    }

    @Test
    void reportsExpiredAndExpiringStatusFromTheItem() {
        User user = cook("pan-status@example.com");
        PantryItemForm form = formFor(ingredient(user, "Yogurt", Unit.GRAM), "500.000", StorageLocation.FRIDGE);
        form.setExpiresOn(TODAY.minusDays(1));
        PantryItem item = pantryService.create(form, user);

        assertTrue(item.isExpired(TODAY));
        assertTrue(item.isExpiringWithin(7, TODAY));
        assertFalse(item.isExpired(TODAY.minusDays(5)));
    }

    @Test
    void rejectsAnIngredientBelongingToAnotherAccount() {
        User mine = cook("pan-foreign-mine@example.com");
        User theirs = cook("pan-foreign-theirs@example.com");
        Ingredient theirSaffron = ingredient(theirs, "Saffron", Unit.GRAM);

        assertThrows(NoSuchElementException.class,
                () -> pantryService.create(
                        formFor(theirSaffron, "1.000", StorageLocation.PANTRY), mine));
    }

    @Test
    void catchesBackwardsDatesBeforeSaving() {
        PantryItemForm form = new PantryItemForm();
        form.setPurchasedOn(TODAY);
        form.setExpiresOn(TODAY.minusDays(2));
        assertTrue(form.hasBackwardsDates());

        form.setExpiresOn(TODAY.plusDays(2));
        assertFalse(form.hasBackwardsDates());
    }

    @Test
    void deletesWithoutBlocking() {
        User user = cook("pan-delete@example.com");
        PantryItem item = pantryService.create(
                formFor(ingredient(user, "Oats", Unit.KILOGRAM), "1.000", StorageLocation.PANTRY), user);

        pantryService.delete(item.getId(), user);
        assertTrue(pantryService.findAll(user).isEmpty());
    }

    @Test
    void reportsTheIngredientStockingUnit() {
        User user = cook("pan-unit@example.com");
        Ingredient flour = ingredient(user, "Flour", Unit.KILOGRAM);
        PantryItem item = pantryService.create(
                formFor(flour, "2.000", StorageLocation.PANTRY), user);

        assertEquals(Unit.KILOGRAM, item.getUnit(),
                "the unit comes from the ingredient, not the shelf row");
    }

    @Test
    void newItemsStartOnTheIngredientDefaultShelf() {
        User user = cook("pan-shelf@example.com");
        IngredientForm form = new IngredientForm();
        form.setName("Frozen peas");
        form.setCategory(IngredientCategory.FROZEN);
        form.setStockUnit(Unit.GRAM);
        form.setDefaultLocation(StorageLocation.FREEZER);
        Ingredient peas = ingredientService.create(form, user);

        PantryItem item = new PantryItem(peas.getUser(), peas, new BigDecimal("400.000"));
        assertEquals(StorageLocation.FREEZER, item.getLocation());
    }
}
