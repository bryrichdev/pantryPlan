package edu.wgu.pantryprep;

import static edu.wgu.pantryprep.importing.ImportWorkbooks.INGREDIENTS;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.PANTRY;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.RECIPES;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.RECIPE_INGREDIENTS;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.WORKBOOK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.IngredientCategory;
import edu.wgu.pantryprep.domain.PantryItem;
import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.RecipeIcon;
import edu.wgu.pantryprep.domain.RecipeLine;
import edu.wgu.pantryprep.domain.StorageLocation;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.importing.ImportProblem;
import edu.wgu.pantryprep.importing.ImportReport;
import edu.wgu.pantryprep.importing.SpreadsheetImportService;
import edu.wgu.pantryprep.repository.IngredientRepository;
import edu.wgu.pantryprep.service.IngredientService;
import edu.wgu.pantryprep.service.PantryService;
import edu.wgu.pantryprep.service.RecipeService;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.IngredientForm;
import edu.wgu.pantryprep.web.form.RegistrationForm;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** Recipe and pantry spreadsheets read, checked and saved against the real schema. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SpreadsheetImportTests {

    @Autowired private UserService userService;
    @Autowired private IngredientService ingredientService;
    @Autowired private IngredientRepository ingredientRepository;
    @Autowired private RecipeService recipeService;
    @Autowired private PantryService pantryService;
    @Autowired private SpreadsheetImportService imports;

    private User user;
    private Ingredient flour;

    @BeforeEach
    void cook() {
        RegistrationForm registration = new RegistrationForm();
        registration.setDisplayName("Cook");
        registration.setEmail("import-" + UUID.randomUUID() + "@example.com");
        registration.setPassword("correcthorsebattery");
        registration.setConfirmPassword("correcthorsebattery");
        user = userService.register(registration);

        IngredientForm form = new IngredientForm();
        form.setName("All-purpose flour");
        form.setCategory(IngredientCategory.PANTRY_STAPLE);
        form.setStockUnit(Unit.GRAM);
        form.setDefaultLocation(StorageLocation.PANTRY);
        form.setGramsPerCup(new BigDecimal("125"));
        flour = ingredientService.create(form, user);
    }

    // ------------------------------------------------------------- recipes

    private byte[] pancakes() {
        return ImportTestWorkbooks.of(WORKBOOK)
                .row(RECIPES, "Buttermilk pancakes", "4", "10", "15", "breakfast", "American", "Pancakes",
                        "weekend, sweet", "Fluffy and quick", "Whisk.\nRest.\nFry.", "smittenkitchen.com/pancakes")
                .row(RECIPES, "Plain toast", "1", "", "", "", "", "", "", "", "")
                .row(RECIPE_INGREDIENTS, "Buttermilk pancakes", "all-purpose FLOUR", "1 1/2", "cups", "sifted", "")
                .row(RECIPE_INGREDIENTS, "Buttermilk pancakes", "Buttermilk", "2", "cup", "", "Dairy")
                .row(RECIPE_INGREDIENTS, "buttermilk pancakes", "Baking powder", "2", "tsp", "", "")
                .row(RECIPE_INGREDIENTS, "Plain toast", "Bread", "2", "pc", "", "Bakery")
                .bytes();
    }

    @Test
    void aPreviewSaysWhatWouldBeAddedAndSavesNothing() {
        ImportReport report = imports.preview(pancakes(), user);

        assertFalse(report.hasProblems(), () -> report.problems().toString());
        assertFalse(report.saved());
        assertEquals(List.of("Buttermilk pancakes", "Plain toast"), report.recipes());
        assertEquals(List.of("Buttermilk", "Baking powder", "Bread"), report.newIngredients());
        assertEquals(4, report.counts().get(1).rows());
        assertTrue(recipeService.findAll(user).isEmpty());
        assertEquals(1, ingredientService.findAll(user).size());
    }

    @Test
    void importingSavesTheRecipesAndAddsTheMissingIngredients() {
        ImportReport report = imports.importFile(pancakes(), user);

        assertTrue(report.saved(), () -> report.problems().toString());
        Recipe pancakes = recipeService.findAll(user).stream()
                .filter(recipe -> recipe.getName().equals("Buttermilk pancakes")).findFirst().orElseThrow();
        pancakes = recipeService.requireOwned(pancakes.getId(), user);
        assertEquals(4, pancakes.getServings());
        assertEquals(10, pancakes.getPrepMinutes());
        assertEquals("Breakfast", pancakes.getMealType());
        assertEquals("American", pancakes.getNationality());
        assertEquals(RecipeIcon.PANCAKES, pancakes.getIcon());
        assertTrue(pancakes.getTags().contains("weekend"));
        assertEquals(3, pancakes.getLines().size());
        assertEquals("https://smittenkitchen.com/pancakes", pancakes.getSourceUrl());

        RecipeLine flourLine = pancakes.getLines().stream()
                .filter(line -> line.getIngredient().getId().equals(flour.getId())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("1.5").compareTo(flourLine.getQuantity()), "matched by name, any case");
        assertEquals(Unit.CUP, flourLine.getUnit());
        assertEquals("sifted", flourLine.getNote());

        Ingredient buttermilk = ingredientRepository.findByUserAndNameIgnoreCase(user, "buttermilk").orElseThrow();
        assertEquals(IngredientCategory.DAIRY, buttermilk.getCategory());
        assertEquals(Unit.CUP, buttermilk.getStockUnit(), "kept in the unit the recipe used");
        assertEquals(StorageLocation.FRIDGE, buttermilk.getDefaultLocation());
        Ingredient powder = ingredientRepository.findByUserAndNameIgnoreCase(user, "Baking powder").orElseThrow();
        assertEquals(IngredientCategory.OTHER, powder.getCategory());
        assertEquals(4, ingredientService.findAll(user).size(), "flour plus the three new ones");
    }

    @Test
    void problemsPointAtTheCellAndNothingIsSaved() {
        imports.importFile(ImportTestWorkbooks.of(WORKBOOK)
                .row(RECIPES, "Chili", "6", "", "", "", "", "", "", "", "").bytes(), user);

        byte[] file = ImportTestWorkbooks.of(WORKBOOK)
                .row(RECIPES, "chili", "6", "", "", "", "", "", "", "", "")
                .row(RECIPES, "Soup", "200", "", "", "Brunch", "", "", "", "", "")
                .row(RECIPES, "Salad", "2", "", "", "", "", "", "", "", "", "not a link")
                .row(RECIPES, "salad", "2", "", "", "", "", "", "", "", "")
                .row(RECIPE_INGREDIENTS, "Stew", "Beef", "1", "lb", "", "")
                .row(RECIPE_INGREDIENTS, "Salad", "Lettuce", "1", "handful", "", "")
                .row(RECIPE_INGREDIENTS, "Salad", "Tomato", "", "pc", "", "")
                .bytes();
        ImportReport report = imports.importFile(file, user);

        assertFalse(report.saved());
        List<String> where = report.problems().stream()
                .map(p -> p.sheet() + " " + p.row() + " " + p.column()).toList();
        assertTrue(where.contains("Recipes 2 Name"), "a name already used: " + where);
        assertTrue(where.contains("Recipes 3 Serves"), "out of range: " + where);
        assertTrue(where.contains("Recipes 3 Meal type"), "not on the list: " + where);
        assertTrue(where.contains("Recipes 5 Name"), "twice in one file: " + where);
        assertTrue(where.contains("Recipes 4 Source link"), "not a web address: " + where);
        assertTrue(where.contains("Recipe Ingredients 2 Recipe"), "no such recipe: " + where);
        assertTrue(where.contains("Recipe Ingredients 3 Unit"), "unknown unit: " + where);
        assertTrue(where.contains("Recipe Ingredients 4 Amount"), "missing amount: " + where);
        assertEquals(1, recipeService.findAll(user).size(), "only the earlier chili");
        assertTrue(ingredientRepository.findByUserAndNameIgnoreCase(user, "Lettuce").isEmpty());
    }

    @Test
    void aFileThatIsNotASpreadsheetIsReportedNotThrown() {
        ImportReport report = imports.preview("name,serves\nchili,4".getBytes(), user);
        assertTrue(report.hasProblems());
        ImportProblem problem = report.problems().getFirst();
        assertEquals(null, problem.sheet());
    }

    // -------------------------------------------------------------- pantry

    @Test
    void pantryRowsConvertToTheStockUnitAndFillInTheBlanks() {
        byte[] file = ImportTestWorkbooks.of(WORKBOOK)
                .row(PANTRY, "All-purpose flour", "2", "cup", "", "2026-01-05", "", "")
                .row(PANTRY, "all-purpose flour", "500", "", "Freezer", "", "", "")
                .row(PANTRY, "Whole milk", "1", "L", "", "1/10/2026", "1/20/2026", "Dairy")
                .row(PANTRY, "Whole milk", "500", "ml", "", "", "", "")
                .bytes();
        ImportReport report = imports.importFile(file, user);

        assertTrue(report.saved(), () -> report.problems().toString());
        assertEquals(List.of("Whole milk"), report.newIngredients());
        List<PantryItem> items = pantryService.findAll(user);
        assertEquals(4, items.size());

        PantryItem cups = items.stream().filter(i -> LocalDate.of(2026, 1, 5).equals(i.getPurchasedOn()))
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("250").compareTo(cups.getQuantity()), "2 cups at 125 g a cup");
        assertEquals(StorageLocation.PANTRY, cups.getLocation(), "the ingredient's usual place");

        PantryItem frozen = items.stream().filter(i -> i.getLocation() == StorageLocation.FREEZER)
                .findFirst().orElseThrow();
        assertEquals(LocalDate.now(), frozen.getPurchasedOn(), "bought today when left blank");

        Ingredient milk = ingredientRepository.findByUserAndNameIgnoreCase(user, "Whole milk").orElseThrow();
        assertEquals(Unit.LITER, milk.getStockUnit());
        PantryItem half = items.stream().filter(i -> i.getIngredient().getId().equals(milk.getId())
                && i.getExpiresOn() == null).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("0.5").compareTo(half.getQuantity()), "500 ml kept in litres");
        assertEquals(StorageLocation.FRIDGE, half.getLocation());
    }

    @Test
    void pantryProblemsStopTheWholeImport() {
        byte[] file = ImportTestWorkbooks.of(WORKBOOK)
                .row(PANTRY, "All-purpose flour", "1", "", "", "", "", "")
                .row(PANTRY, "Saffron", "1", "", "", "", "", "Spice")
                .row(PANTRY, "All-purpose flour", "3", "pc", "", "", "", "")
                .row(PANTRY, "All-purpose flour", "1", "", "", "2026-03-01", "2026-02-01", "")
                .row(PANTRY, "All-purpose flour", "-1", "", "", "", "", "")
                .bytes();
        ImportReport report = imports.importFile(file, user);

        assertFalse(report.saved());
        List<String> where = report.problems().stream()
                .map(p -> p.row() + " " + p.column()).toList();
        assertTrue(where.contains("3 Unit"), "a new ingredient needs a unit: " + where);
        assertTrue(where.contains("4 Unit"), "pieces can't become grams: " + where);
        assertTrue(where.contains("5 Expires"), "expiry before purchase: " + where);
        assertTrue(where.contains("6 Amount"), "negative amount: " + where);
        assertTrue(pantryService.findAll(user).isEmpty(), "the good first row isn't saved alone");
        assertTrue(ingredientRepository.findByUserAndNameIgnoreCase(user, "Saffron").isEmpty());
    }

    @Test
    void anEmptyTemplateSaysThereIsNothingToImport() {
        ImportReport report = imports.preview(WORKBOOK.template(), user);
        assertTrue(report.hasProblems());
        assertTrue(report.problems().getFirst().message().contains("nothing to import"));
    }

    // ------------------------------------------------------- one workbook

    @Test
    void oneFileCanBringInIngredientsRecipesAndPantryTogether() {
        byte[] file = ImportTestWorkbooks.of(WORKBOOK)
                .row(INGREDIENTS, "Smoked paprika", "Spice", "g", "", "50", "")
                .row(INGREDIENTS, "Bread flour", "Pantry staple", "g", "Pantry", "1000", "127")
                .row(INGREDIENTS, "all-purpose flour", "Other", "kg", "", "", "")
                .row(RECIPES, "Chili", "6", "", "", "", "", "", "", "", "")
                .row(RECIPE_INGREDIENTS, "Chili", "Smoked paprika", "1", "tbsp", "", "")
                .row(RECIPE_INGREDIENTS, "Chili", "Kidney beans", "2", "cup", "", "Pantry staple")
                .row(PANTRY, "Bread flour", "2", "cup", "", "", "")
                .row(PANTRY, "Smoked paprika", "40", "", "", "", "")
                .bytes();

        ImportReport preview = imports.preview(file, user);
        assertFalse(preview.hasProblems(), () -> preview.problems().toString());
        assertEquals(List.of("Chili"), preview.recipes());
        assertEquals(2, preview.pantryItems());
        assertEquals(List.of("Smoked paprika", "Bread flour", "Kidney beans"), preview.newIngredients());
        assertEquals(List.of("All-purpose flour"), preview.unchangedIngredients(), "an existing one is left alone");
        assertEquals("1 recipe, 2 pantry items and 3 new ingredients", preview.summary());

        ImportReport report = imports.importFile(file, user);
        assertTrue(report.saved());

        Ingredient paprika = ingredientRepository.findByUserAndNameIgnoreCase(user, "smoked paprika").orElseThrow();
        assertEquals(IngredientCategory.SPICE, paprika.getCategory());
        assertEquals(Unit.GRAM, paprika.getStockUnit());
        assertEquals(StorageLocation.SPICE_RACK, paprika.getDefaultLocation(), "from its category");
        assertEquals(0, new BigDecimal("50").compareTo(paprika.getDefaultQuantity()));

        Ingredient breadFlour = ingredientRepository.findByUserAndNameIgnoreCase(user, "Bread flour").orElseThrow();
        assertEquals(0, new BigDecimal("127").compareTo(breadFlour.getGramsPerCup()));
        PantryItem flourOnShelf = pantryService.findAll(user).stream()
                .filter(item -> item.getIngredient().getId().equals(breadFlour.getId())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("254").compareTo(flourOnShelf.getQuantity()),
                "2 cups converted with the weight per cup the Ingredients sheet gave");

        assertEquals(Unit.GRAM, ingredientRepository.findByUserAndNameIgnoreCase(user, "All-purpose flour")
                .orElseThrow().getStockUnit(), "the existing flour keeps its unit");
        assertEquals(1, recipeService.findAll(user).size());
    }

    @Test
    void theIngredientsSheetListsEachIngredientOnce() {
        byte[] file = ImportTestWorkbooks.of(WORKBOOK)
                .row(INGREDIENTS, "Saffron", "Spice", "g", "", "", "")
                .row(INGREDIENTS, "saffron", "Spice", "g", "", "", "")
                .row(INGREDIENTS, "Rice", "", "", "", "", "")
                .bytes();
        ImportReport report = imports.importFile(file, user);
        assertFalse(report.saved());
        List<String> where = report.problems().stream().map(p -> p.sheet() + " " + p.row() + " " + p.column())
                .toList();
        assertTrue(where.contains("Ingredients 3 Name"), "a repeat: " + where);
        assertTrue(where.contains("Ingredients 4 Kept in"), "the unit is needed: " + where);
        assertTrue(ingredientRepository.findByUserAndNameIgnoreCase(user, "Saffron").isEmpty());
    }
}
