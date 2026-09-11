package edu.wgu.pantryplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.IngredientCategory;
import edu.wgu.pantryplan.domain.StorageLocation;
import edu.wgu.pantryplan.domain.Unit;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.repository.IngredientPresetRepository;
import edu.wgu.pantryplan.service.IngredientService;
import edu.wgu.pantryplan.service.UserService;
import edu.wgu.pantryplan.web.form.IngredientForm;
import edu.wgu.pantryplan.web.form.RegistrationForm;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class IngredientPresetTests {

    @Autowired
    private IngredientService ingredientService;

    @Autowired
    private IngredientPresetRepository presetRepository;

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

    private Ingredient find(User user, String name) {
        return ingredientService.findAll(user).stream()
                .filter(ingredient -> ingredient.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void seedsACatalogueOfPresets() {
        assertTrue(presetRepository.count() > 50,
                "the migration should seed a usable starter catalogue");
    }

    @Test
    void importsEveryPresetIntoAnEmptyAccount() {
        User user = cook("preset-import@example.com");
        long available = presetRepository.count();

        int added = ingredientService.importPresets(user);

        assertEquals(available, added);
        assertEquals(available, ingredientService.findAll(user).size());
    }

    @Test
    void carriesDensityUnitAndShelfAcross() {
        User user = cook("preset-fields@example.com");
        ingredientService.importPresets(user);

        Ingredient flour = find(user, "All-purpose flour");
        assertEquals(IngredientCategory.PANTRY_STAPLE, flour.getCategory());
        assertEquals(Unit.GRAM, flour.getStockUnit());
        assertEquals(StorageLocation.PANTRY, flour.getDefaultLocation());
        assertEquals(0, new BigDecimal("120.000").compareTo(flour.getGramsPerCup()));
        assertTrue(flour.hasVolumeWeightRatio());

        Ingredient butter = find(user, "Butter");
        assertEquals(StorageLocation.FRIDGE, butter.getDefaultLocation());

        Ingredient eggs = find(user, "Eggs");
        assertEquals(Unit.PIECE, eggs.getStockUnit());
        assertNull(eggs.getGramsPerCup(),
                "things counted rather than measured carry no density");
    }

    @Test
    void seedsUsualAmountsForEveryPackagedPreset() {
        /* V6 matches presets by name, so a misspelt name there updates nothing
           and fails silently. Counting the seeded rows catches that. Change
           this number only alongside a migration that changes the seed. */
        long seeded = presetRepository.findAll().stream()
                .filter(preset -> preset.getDefaultQuantity() != null)
                .count();
        assertEquals(62, seeded);
    }

    @Test
    void carriesTheUsualAmountAcross() {
        User user = cook("preset-usual@example.com");
        ingredientService.importPresets(user);

        Ingredient eggs = find(user, "Eggs");
        assertEquals(0, new BigDecimal("12").compareTo(eggs.getDefaultQuantity()),
                "a dozen, counted in pieces");

        Ingredient milk = find(user, "Whole milk");
        assertEquals(0, new BigDecimal("3785").compareTo(milk.getDefaultQuantity()),
                "a gallon, kept in millilitres");

        assertNull(find(user, "Chicken breast").getDefaultQuantity(),
                "meat sold by variable weight has no usual amount");
    }

    @Test
    void runningTwiceAddsNothingTheSecondTime() {
        User user = cook("preset-twice@example.com");
        int first = ingredientService.importPresets(user);
        int second = ingredientService.importPresets(user);

        assertTrue(first > 0);
        assertEquals(0, second);
        assertEquals(first, ingredientService.findAll(user).size());
    }

    @Test
    void skipsNamesTheAccountAlreadyUsesRegardlessOfCase() {
        User user = cook("preset-collide@example.com");

        IngredientForm mine = new IngredientForm();
        mine.setName("all-purpose FLOUR");
        mine.setCategory(IngredientCategory.OTHER);
        mine.setStockUnit(Unit.POUND);
        mine.setDefaultLocation(StorageLocation.FREEZER);
        ingredientService.create(mine, user);

        ingredientService.importPresets(user);

        List<Ingredient> matches = ingredientService.findAll(user).stream()
                .filter(ingredient -> ingredient.getName().equalsIgnoreCase("all-purpose flour"))
                .toList();

        assertEquals(1, matches.size(), "no duplicate was created");
        assertEquals(Unit.POUND, matches.get(0).getStockUnit(),
                "the cook's own settings are left alone");
        assertEquals(StorageLocation.FREEZER, matches.get(0).getDefaultLocation());
    }

    @Test
    void reportsHowManyAreStillMissing() {
        User user = cook("preset-count@example.com");
        long available = presetRepository.count();

        assertEquals(available, ingredientService.countMissingPresets(user));
        ingredientService.importPresets(user);
        assertEquals(0, ingredientService.countMissingPresets(user));
    }

    @Test
    void importedIngredientsBelongToTheImportingAccountOnly() {
        User mine = cook("preset-mine@example.com");
        User theirs = cook("preset-theirs@example.com");

        ingredientService.importPresets(mine);

        assertTrue(ingredientService.findAll(theirs).isEmpty(),
                "importing does not touch other accounts");
        assertNotNull(find(mine, "Butter"));
    }
}
