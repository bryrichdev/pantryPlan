package edu.wgu.pantryplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.IngredientCategory;
import edu.wgu.pantryplan.domain.PantryItem;
import edu.wgu.pantryplan.domain.StorageLocation;
import edu.wgu.pantryplan.domain.Unit;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.service.IngredientService;
import edu.wgu.pantryplan.service.PantryService;
import edu.wgu.pantryplan.service.UserService;
import edu.wgu.pantryplan.web.form.IngredientForm;
import edu.wgu.pantryplan.web.form.PantryItemForm;
import edu.wgu.pantryplan.web.form.RegistrationForm;
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

    private Ingredient ingredient(User user, String name) {
        IngredientForm form = new IngredientForm();
        form.setName(name);
        form.setCategory(IngredientCategory.OTHER);
        return ingredientService.create(form, user);
    }

    private PantryItemForm formFor(Ingredient ingredient, String quantity, Unit unit,
                                   StorageLocation location) {
        PantryItemForm form = new PantryItemForm();
        form.setIngredientId(ingredient.getId());
        form.setQuantity(new BigDecimal(quantity));
        form.setUnit(unit);
        form.setLocation(location);
        return form;
    }

    @Test
    void createsAndListsItemsSortedByIngredientName() {
        User user = cook("pan-create@example.com");
        pantryService.create(formFor(ingredient(user, "Rice"), "500.000", Unit.GRAM,
                StorageLocation.PANTRY), user);
        pantryService.create(formFor(ingredient(user, "Butter"), "225.000", Unit.GRAM,
                StorageLocation.FRIDGE), user);

        var all = pantryService.findAll(user);
        assertEquals(2, all.size());
        assertEquals("Butter", all.get(0).getIngredient().getName());
    }

    @Test
    void keepsItemsScopedToTheOwningAccount() {
        User mine = cook("pan-mine@example.com");
        User theirs = cook("pan-theirs@example.com");
        PantryItem item = pantryService.create(
                formFor(ingredient(mine, "Saffron"), "2.000", Unit.GRAM, StorageLocation.SPICE_RACK), mine);

        assertTrue(pantryService.findAll(theirs).isEmpty());
        assertThrows(NoSuchElementException.class,
                () -> pantryService.requireOwned(item.getId(), theirs));
    }

    @Test
    void searchCombinesTermAndLocation() {
        User user = cook("pan-search@example.com");
        pantryService.create(formFor(ingredient(user, "Cheddar cheese"), "200.000", Unit.GRAM,
                StorageLocation.FRIDGE), user);
        pantryService.create(formFor(ingredient(user, "Cream cheese"), "150.000", Unit.GRAM,
                StorageLocation.FRIDGE), user);
        pantryService.create(formFor(ingredient(user, "Cheese crackers"), "1.000", Unit.PIECE,
                StorageLocation.PANTRY), user);

        assertEquals(3, pantryService.search(user, "cheese", null).size());
        assertEquals(2, pantryService.search(user, "cheese", StorageLocation.FRIDGE).size());
        assertEquals(1, pantryService.search(user, null, StorageLocation.PANTRY).size());
        assertEquals(3, pantryService.search(user, "  ", null).size(),
                "a blank search returns everything");
    }

    @Test
    void findsItemsExpiringWithinAWindow() {
        User user = cook("pan-expiry@example.com");

        PantryItemForm soon = formFor(ingredient(user, "Milk"), "1.000", Unit.LITER,
                StorageLocation.FRIDGE);
        soon.setExpiresOn(TODAY.plusDays(3));
        pantryService.create(soon, user);

        PantryItemForm later = formFor(ingredient(user, "Flour"), "1.000", Unit.KILOGRAM,
                StorageLocation.PANTRY);
        later.setExpiresOn(TODAY.plusDays(60));
        pantryService.create(later, user);

        PantryItemForm never = formFor(ingredient(user, "Salt"), "500.000", Unit.GRAM,
                StorageLocation.PANTRY);
        pantryService.create(never, user);

        assertEquals(1, pantryService.findExpiringWithin(user, 7, TODAY).size());
        assertEquals(2, pantryService.findExpiringWithin(user, 90, TODAY).size(),
                "items with no expiry date are never reported as expiring");
    }

    @Test
    void reportsExpiredAndExpiringStatusFromTheItem() {
        User user = cook("pan-status@example.com");
        PantryItemForm form = formFor(ingredient(user, "Yogurt"), "500.000", Unit.GRAM,
                StorageLocation.FRIDGE);
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
        Ingredient theirSaffron = ingredient(theirs, "Saffron");

        assertThrows(NoSuchElementException.class,
                () -> pantryService.create(
                        formFor(theirSaffron, "1.000", Unit.GRAM, StorageLocation.PANTRY), mine));
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
                formFor(ingredient(user, "Oats"), "1.000", Unit.KILOGRAM, StorageLocation.PANTRY), user);

        pantryService.delete(item.getId(), user);
        assertTrue(pantryService.findAll(user).isEmpty());
    }
}
