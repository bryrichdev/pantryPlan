package edu.wgu.pantryplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.IngredientCategory;
import edu.wgu.pantryplan.domain.Recipe;
import edu.wgu.pantryplan.domain.StorageLocation;
import edu.wgu.pantryplan.domain.Unit;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.report.PantryStockReport;
import edu.wgu.pantryplan.report.Report;
import edu.wgu.pantryplan.service.IngredientService;
import edu.wgu.pantryplan.service.PantryService;
import edu.wgu.pantryplan.service.RecipeService;
import edu.wgu.pantryplan.service.ReportService;
import edu.wgu.pantryplan.service.UserService;
import edu.wgu.pantryplan.web.form.IngredientForm;
import edu.wgu.pantryplan.web.form.PantryItemForm;
import edu.wgu.pantryplan.web.form.RecipeForm;
import edu.wgu.pantryplan.web.form.RegistrationForm;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ReportServiceTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 11);

    @Autowired
    private ReportService reportService;

    @Autowired
    private IngredientService ingredientService;

    @Autowired
    private PantryService pantryService;

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
        form.setCategory(IngredientCategory.PRODUCE);
        form.setStockUnit(Unit.GRAM);
        form.setDefaultLocation(StorageLocation.FRIDGE);
        return ingredientService.create(form, user);
    }

    private void stock(User user, Ingredient ingredient, String quantity, LocalDate expiresOn) {
        PantryItemForm form = new PantryItemForm();
        form.setIngredientId(ingredient.getId());
        form.setQuantity(new BigDecimal(quantity));
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

    @Test
    void pantryStockTotalsRowsPerIngredientAndKeepsAccountsSeparate() {
        User mine = cook("report-stock-mine@example.com");
        User theirs = cook("report-stock-theirs@example.com");
        Ingredient carrots = ingredient(mine, "Carrots");
        stock(mine, carrots, "125", null);
        stock(mine, carrots, "75", TODAY.plusDays(5));
        stock(theirs, ingredient(theirs, "Private carrots"), "999", null);

        Report report = reportService.pantryStock(mine);

        assertTrue(report instanceof PantryStockReport);
        assertEquals("Pantry stock report", report.getTitle());
        assertNotNull(report.getGeneratedAt());
        assertEquals(1, report.getRows().size());
        assertEquals("Carrots", report.getRows().getFirst().getValues().get(0));
        assertEquals("Produce", report.getRows().getFirst().getValues().get(1));
        assertEquals("200 g", report.getRows().getFirst().getValues().get(2));
    }

    @Test
    void recipeUsageOrdersByCookingFrequencyAndIncludesNeverCookedRecipes() {
        User user = cook("report-usage@example.com");
        Recipe favorite = recipe(user, "Favorite soup");
        recipe(user, "Untested toast");
        favorite.recordCooked(Instant.parse("2026-09-11T12:00:00Z"));
        favorite.recordCooked(Instant.parse("2026-09-11T13:00:00Z"));

        Report report = reportService.recipeUsage(user);

        assertEquals("Recipe usage report", report.getTitle());
        assertEquals(2, report.getRows().size());
        assertEquals("Favorite soup", report.getRows().getFirst().getValues().get(0));
        assertEquals("2", report.getRows().getFirst().getValues().get(2));
        assertEquals("Untested toast", report.getRows().get(1).getValues().get(0));
        assertEquals("Not cooked yet", report.getRows().get(1).getValues().get(3));
    }

    @Test
    void expiryReportsSplitNearExpiryAndAlreadyExpiredItems() {
        User user = cook("report-expiry@example.com");
        stock(user, ingredient(user, "Old yogurt"), "500", TODAY.minusDays(2));
        stock(user, ingredient(user, "Use today"), "100", TODAY);
        stock(user, ingredient(user, "Use soon"), "100", TODAY.plusDays(4));
        stock(user, ingredient(user, "Later"), "100", TODAY.plusDays(8));
        stock(user, ingredient(user, "No date"), "100", null);

        Report nearExpiry = reportService.nearExpiredPantryItems(user, TODAY);
        Report expired = reportService.expiredPantryItems(user, TODAY);

        assertEquals("Near-expired pantry items", nearExpiry.getTitle());
        assertEquals(2, nearExpiry.getRows().size());
        assertEquals("Use today", nearExpiry.getRows().getFirst().getValues().get(0));
        assertEquals("Expires today", nearExpiry.getRows().getFirst().getValues().get(4));
        assertTrue(nearExpiry.getRows().stream().noneMatch(row -> row.getValues().contains("Later")));

        assertEquals("Expired pantry items", expired.getTitle());
        assertEquals(1, expired.getRows().size());
        assertEquals("Old yogurt", expired.getRows().getFirst().getValues().get(0));
        assertEquals("2 days overdue", expired.getRows().getFirst().getValues().get(4));
        assertFalse(expired.getRows().stream().anyMatch(row -> row.getValues().contains("Use today")));
    }
}
