package edu.wgu.pantryplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.IngredientCategory;
import edu.wgu.pantryplan.domain.Recipe;
import edu.wgu.pantryplan.domain.RecipeLine;
import edu.wgu.pantryplan.domain.Unit;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.repository.RecipeRepository;
import edu.wgu.pantryplan.service.IngredientInUseException;
import edu.wgu.pantryplan.service.IngredientService;
import edu.wgu.pantryplan.service.UserService;
import edu.wgu.pantryplan.web.form.IngredientForm;
import edu.wgu.pantryplan.web.form.RegistrationForm;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Validator;
import java.math.BigDecimal;
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
class IngredientServiceTests {

    @Autowired
    private IngredientService ingredientService;

    @Autowired
    private UserService userService;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private Validator validator;

    @PersistenceContext
    private EntityManager entityManager;

    private User cook(String email) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName("Cook");
        form.setEmail(email);
        form.setPassword("correcthorsebattery");
        form.setConfirmPassword("correcthorsebattery");
        return userService.register(form);
    }

    private IngredientForm formFor(String name, IngredientCategory category) {
        IngredientForm form = new IngredientForm();
        form.setName(name);
        form.setCategory(category);
        return form;
    }

    @Test
    void createsAndListsIngredients() {
        User user = cook("ing-create@example.com");

        ingredientService.create(formFor("Basil", IngredientCategory.PRODUCE), user);
        ingredientService.create(formFor("Almond flour", IngredientCategory.PANTRY_STAPLE), user);

        List<Ingredient> all = ingredientService.findAll(user);
        assertEquals(2, all.size());
        assertEquals("Almond flour", all.get(0).getName(), "results should be sorted by name");
    }

    @Test
    void keepsIngredientsScopedToTheOwningAccount() {
        User mine = cook("ing-mine@example.com");
        User theirs = cook("ing-theirs@example.com");

        Ingredient secret = ingredientService.create(formFor("Saffron", IngredientCategory.SPICE), mine);

        assertTrue(ingredientService.findAll(theirs).isEmpty(),
                "another account must not see these ingredients");
        assertThrows(NoSuchElementException.class,
                () -> ingredientService.requireOwned(secret.getId(), theirs),
                "another account must not load this ingredient by id");
    }

    @Test
    void searchIsCaseInsensitiveAndPartial() {
        User user = cook("ing-search@example.com");
        ingredientService.create(formFor("Cheddar cheese", IngredientCategory.DAIRY), user);
        ingredientService.create(formFor("Cream cheese", IngredientCategory.DAIRY), user);
        ingredientService.create(formFor("Carrots", IngredientCategory.PRODUCE), user);

        assertEquals(2, ingredientService.search(user, "CHEESE").size());
        assertEquals(1, ingredientService.search(user, "carr").size());
        assertEquals(3, ingredientService.search(user, "  ").size(),
                "a blank search returns everything");
        assertTrue(ingredientService.search(user, "zzz").isEmpty());
    }

    @Test
    void detectsDuplicateNamesButAllowsRenamingItself() {
        User user = cook("ing-dupe@example.com");
        Ingredient butter = ingredientService.create(formFor("Butter", IngredientCategory.DAIRY), user);

        assertTrue(ingredientService.nameCollides(user, "butter", null),
                "a new ingredient may not reuse the name");
        assertFalse(ingredientService.nameCollides(user, "Butter", butter.getId()),
                "the same ingredient may keep its own name while editing");
    }

    @Test
    void updatesFieldsInPlace() {
        User user = cook("ing-update@example.com");
        Ingredient sugar = ingredientService.create(formFor("Sugar", IngredientCategory.OTHER), user);

        IngredientForm edit = formFor("Granulated sugar", IngredientCategory.PANTRY_STAPLE);
        edit.setId(sugar.getId());
        edit.setGramsPerCup(new BigDecimal("200.000"));
        ingredientService.update(sugar.getId(), edit, user);

        Ingredient reloaded = ingredientService.requireOwned(sugar.getId(), user);
        assertEquals("Granulated sugar", reloaded.getName());
        assertEquals(IngredientCategory.PANTRY_STAPLE, reloaded.getCategory());
        assertNotNull(reloaded.getGramsPerCup());
        assertTrue(reloaded.hasVolumeWeightRatio());
    }

    @Test
    void deletesAnUnreferencedIngredient() {
        User user = cook("ing-delete@example.com");
        Ingredient nutmeg = ingredientService.create(formFor("Nutmeg", IngredientCategory.SPICE), user);

        assertTrue(ingredientService.isDeletable(nutmeg));
        ingredientService.delete(nutmeg.getId(), user);

        assertTrue(ingredientService.findAll(user).isEmpty());
    }

    @Test
    void refusesToDeleteAnIngredientUsedByARecipe() {
        User user = cook("ing-inuse@example.com");
        Ingredient oats = ingredientService.create(formFor("Oats", IngredientCategory.PANTRY_STAPLE), user);

        Recipe porridge = new Recipe(user, "Porridge", 2);
        porridge.addLine(new RecipeLine(oats, new BigDecimal("1.000"), Unit.CUP));
        recipeRepository.save(porridge);

        assertFalse(ingredientService.isDeletable(oats));
        IngredientInUseException thrown = assertThrows(IngredientInUseException.class,
                () -> ingredientService.delete(oats.getId(), user));
        assertEquals("Oats", thrown.getIngredientName());
        assertTrue(thrown.getMessage().contains("recipes"));
    }

    @Test
    void storesAndClearsTheUsualAmount() {
        User user = cook("ing-usual@example.com");
        IngredientForm form = formFor("Eggs", IngredientCategory.DAIRY);
        form.setStockUnit(Unit.PIECE);
        form.setDefaultQuantity(new BigDecimal("12"));
        Ingredient eggs = ingredientService.create(form, user);

        /* Flush and clear so the reload comes from the database, not the
           persistence context. That exercises the NUMERIC column itself. */
        entityManager.flush();
        entityManager.clear();

        Ingredient reloaded = ingredientService.requireOwned(eggs.getId(), user);
        assertEquals(0, new BigDecimal("12").compareTo(reloaded.getDefaultQuantity()),
                "the usual amount should survive a round trip");

        form.setId(eggs.getId());
        form.setDefaultQuantity(null);
        ingredientService.update(eggs.getId(), form, user);
        entityManager.flush();
        entityManager.clear();

        assertNull(ingredientService.requireOwned(eggs.getId(), user).getDefaultQuantity(),
                "blanking the field should clear the usual amount");
    }

    @Test
    void rejectsAUsualAmountOfZero() {
        IngredientForm form = formFor("Milk", IngredientCategory.DAIRY);

        form.setDefaultQuantity(BigDecimal.ZERO);
        assertFalse(validator.validateProperty(form, "defaultQuantity").isEmpty(),
                "zero is not a usual amount");

        form.setDefaultQuantity(new BigDecimal("1.5"));
        assertTrue(validator.validateProperty(form, "defaultQuantity").isEmpty(),
                "a fractional amount is allowed");

        form.setDefaultQuantity(null);
        assertTrue(validator.validateProperty(form, "defaultQuantity").isEmpty(),
                "the usual amount is optional");
    }
}
