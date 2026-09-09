package edu.wgu.pantryplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.IngredientCategory;
import edu.wgu.pantryplan.domain.Recipe;
import edu.wgu.pantryplan.domain.Unit;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.repository.RecipeLineRepository;
import edu.wgu.pantryplan.service.IngredientService;
import edu.wgu.pantryplan.service.RecipeService;
import edu.wgu.pantryplan.service.UserService;
import edu.wgu.pantryplan.web.form.IngredientForm;
import edu.wgu.pantryplan.web.form.RecipeForm;
import edu.wgu.pantryplan.web.form.RecipeLineForm;
import edu.wgu.pantryplan.web.form.RegistrationForm;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class RecipeLineTests {

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private IngredientService ingredientService;

    @Autowired
    private UserService userService;

    @Autowired
    private RecipeLineRepository recipeLineRepository;

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

    private RecipeLineForm lineFor(Ingredient ingredient, String quantity, Unit unit) {
        RecipeLineForm line = new RecipeLineForm();
        line.setIngredientId(ingredient.getId());
        line.setQuantity(new BigDecimal(quantity));
        line.setUnit(unit);
        return line;
    }

    private RecipeForm recipeFormFor(String name, int servings) {
        RecipeForm form = new RecipeForm();
        form.setName(name);
        form.setServings(servings);
        return form;
    }

    @Test
    void savesLinesAlongsideTheRecipe() {
        User user = cook("line-create@example.com");
        Ingredient flour = ingredient(user, "Flour");
        Ingredient sugar = ingredient(user, "Sugar");

        RecipeForm form = recipeFormFor("Shortbread", 8);
        form.getLines().add(lineFor(flour, "2.000", Unit.CUP));
        form.getLines().add(lineFor(sugar, "0.500", Unit.CUP));

        Recipe saved = recipeService.create(form, user);
        Recipe reloaded = recipeService.requireOwned(saved.getId(), user);

        assertEquals(2, reloaded.getLines().size());
        assertEquals("Flour", reloaded.getLines().get(0).getIngredient().getName());
        assertEquals(Unit.CUP, reloaded.getLines().get(0).getUnit());
    }

    @Test
    void blankRowsAreDiscardedRatherThanSaved() {
        User user = cook("line-blank@example.com");
        Ingredient flour = ingredient(user, "Flour");

        RecipeForm form = recipeFormFor("Flatbread", 4);
        form.getLines().add(lineFor(flour, "3.000", Unit.CUP));
        form.getLines().add(new RecipeLineForm());
        form.removeBlankLines();

        assertEquals(1, form.getLines().size(), "the untouched row is dropped");

        Recipe saved = recipeService.create(form, user);
        assertEquals(1, recipeService.requireOwned(saved.getId(), user).getLines().size());
    }

    @Test
    void updateReplacesLinesAndDeletesTheOldRows() {
        User user = cook("line-update@example.com");
        Ingredient flour = ingredient(user, "Flour");
        Ingredient oats = ingredient(user, "Oats");

        RecipeForm create = recipeFormFor("Porridge", 2);
        create.getLines().add(lineFor(flour, "1.000", Unit.CUP));
        Recipe saved = recipeService.create(create, user);

        RecipeForm edit = recipeFormFor("Porridge", 2);
        edit.setId(saved.getId());
        edit.getLines().add(lineFor(oats, "2.000", Unit.CUP));
        recipeService.update(saved.getId(), edit, user);

        Recipe reloaded = recipeService.requireOwned(saved.getId(), user);
        assertEquals(1, reloaded.getLines().size());
        assertEquals("Oats", reloaded.getLines().get(0).getIngredient().getName());
        assertTrue(recipeLineRepository.findAllByRecipe(reloaded).size() == 1,
                "the replaced row should be gone, not orphaned");
    }

    @Test
    void refusesAnIngredientBelongingToAnotherAccount() {
        User mine = cook("line-mine@example.com");
        User theirs = cook("line-theirs@example.com");
        Ingredient theirSaffron = ingredient(theirs, "Saffron");

        RecipeForm form = recipeFormFor("Paella", 4);
        form.getLines().add(lineFor(theirSaffron, "1.000", Unit.TEASPOON));

        assertThrows(NoSuchElementException.class, () -> recipeService.create(form, mine));
    }

    @Test
    void scalesASavedLineToPlannedServings() {
        User user = cook("line-scale@example.com");
        Ingredient rice = ingredient(user, "Rice");

        RecipeForm form = recipeFormFor("Rice Bowl", 4);
        form.getLines().add(lineFor(rice, "2.000", Unit.CUP));
        Recipe saved = recipeService.create(form, user);

        Recipe reloaded = recipeService.requireOwned(saved.getId(), user);
        assertEquals(0, new BigDecimal("3").compareTo(reloaded.getLines().get(0).scaledTo(6)));
    }

    @Test
    void searchByIngredientFindsRecipesThroughTheirLines() {
        User user = cook("line-search@example.com");
        Ingredient basil = ingredient(user, "Basil");

        RecipeForm form = recipeFormFor("Pesto", 4);
        form.getLines().add(lineFor(basil, "1.000", Unit.CUP));
        recipeService.create(form, user);
        recipeService.create(recipeFormFor("Toast", 1), user);

        assertEquals(1, recipeService.search(user, "", "basil", "").size());
        assertEquals(2, recipeService.search(user, "", "", "").size());
    }
}
