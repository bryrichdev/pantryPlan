package edu.wgu.pantryprep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.IngredientCategory;
import edu.wgu.pantryprep.domain.MealPlan;
import edu.wgu.pantryprep.domain.MealSlot;
import edu.wgu.pantryprep.domain.PlanEntry;
import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.RecipeLine;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.repository.MealPlanRepository;
import edu.wgu.pantryprep.repository.RecipeRepository;
import edu.wgu.pantryprep.repository.RecipePresetRepository;
import edu.wgu.pantryprep.service.IngredientService;
import edu.wgu.pantryprep.service.RecipeInUseException;
import edu.wgu.pantryprep.service.RecipeService;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.IngredientForm;
import edu.wgu.pantryprep.web.form.RecipeForm;
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
class RecipeServiceTests {

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private IngredientService ingredientService;

    @Autowired
    private UserService userService;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private RecipePresetRepository recipePresetRepository;

    @Autowired
    private MealPlanRepository mealPlanRepository;

    private User cook(String email) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName("Cook");
        form.setEmail(email);
        form.setPassword("correcthorsebattery");
        form.setConfirmPassword("correcthorsebattery");
        return userService.register(form);
    }

    private RecipeForm formFor(String name, int servings) {
        RecipeForm form = new RecipeForm();
        form.setName(name);
        form.setServings(servings);
        form.setPrepMinutes(10);
        form.setCookMinutes(20);
        return form;
    }

    @Test
    void createsRecipeWithNormalisedTags() {
        User user = cook("rec-create@example.com");
        RecipeForm form = formFor("Pesto Pasta", 4);
        form.setTagsCsv("  Italian ,WEEKNIGHT,, italian  ");

        Recipe saved = recipeService.create(form, user);

        assertEquals(2, saved.getTags().size(), "blanks dropped and duplicates folded");
        assertTrue(saved.getTags().contains("italian"));
        assertTrue(saved.getTags().contains("weeknight"));
        assertEquals(30, saved.totalMinutes());
    }

    @Test
    void importsStarterRecipesWithTheirClassificationsAndIngredients() {
        User user = cook("recipe-presets@example.com");

        int added = recipeService.importPresets(user);

        assertEquals(52, recipePresetRepository.count(),
                "twelve original recipes plus forty additional starters");
        assertEquals(recipePresetRepository.count(), added);
        Recipe tacos = recipeService.findAll(user).stream()
                .filter(recipe -> recipe.getName().equals("Black Bean Tacos"))
                .findFirst()
                .orElseThrow();
        assertEquals("Dinner", tacos.getMealType());
        assertEquals("Mexican", tacos.getNationality());
        assertFalse(tacos.getLines().isEmpty(), "preset ingredients are copied with the recipe");
        assertEquals(0, recipeService.importPresets(user), "a second import skips duplicates");
    }

    @Test
    void keepsRecipesScopedToTheOwningAccount() {
        User mine = cook("rec-mine@example.com");
        User theirs = cook("rec-theirs@example.com");
        Recipe secret = recipeService.create(formFor("Family Chili", 6), mine);

        assertTrue(recipeService.findAll(theirs).isEmpty());
        assertThrows(NoSuchElementException.class,
                () -> recipeService.requireOwned(secret.getId(), theirs));
    }

    @Test
    void searchesByNameIngredientAndTagIndependently() {
        User user = cook("rec-search@example.com");

        IngredientForm basilForm = new IngredientForm();
        basilForm.setName("Basil");
        basilForm.setCategory(IngredientCategory.PRODUCE);
        Ingredient basil = ingredientService.create(basilForm, user);

        RecipeForm pestoForm = formFor("Pesto Pasta", 4);
        pestoForm.setTagsCsv("italian");
        Recipe pesto = recipeService.create(pestoForm, user);
        pesto.addLine(new RecipeLine(basil, new BigDecimal("1.000"), Unit.CUP));
        recipeRepository.save(pesto);

        recipeService.create(formFor("Beef Stew", 6), user);

        assertEquals(2, recipeService.search(user, "", "", "").size());
        assertEquals(1, recipeService.search(user, "pesto", "", "").size());
        assertEquals(1, recipeService.search(user, "", "basil", "").size());
        assertEquals(1, recipeService.search(user, "", "", "italian").size());
        assertTrue(recipeService.search(user, "stew", "", "italian").isEmpty(),
                "criteria combine with AND, not OR");
    }

    @Test
    void updateReplacesTagsRatherThanAppending() {
        User user = cook("rec-update@example.com");
        RecipeForm form = formFor("Soup", 4);
        form.setTagsCsv("winter, easy");
        Recipe soup = recipeService.create(form, user);

        RecipeForm edit = formFor("Soup", 8);
        edit.setId(soup.getId());
        edit.setTagsCsv("summer");
        recipeService.update(soup.getId(), edit, user);

        Recipe reloaded = recipeService.requireOwned(soup.getId(), user);
        assertEquals(1, reloaded.getTags().size());
        assertTrue(reloaded.getTags().contains("summer"));
        assertEquals(8, reloaded.getServings());
    }

    @Test
    void refusesToDeleteARecipeScheduledInAMealPlan() {
        User user = cook("rec-inuse@example.com");
        Recipe recipe = recipeService.create(formFor("Sunday Roast", 6), user);

        MealPlan plan = new MealPlan(user, "This week", LocalDate.of(2026, 9, 7));
        plan.addEntry(new PlanEntry(recipe, LocalDate.of(2026, 9, 13), MealSlot.DINNER, 6));
        mealPlanRepository.save(plan);

        RecipeInUseException thrown = assertThrows(RecipeInUseException.class,
                () -> recipeService.delete(recipe.getId(), user));
        assertEquals("Sunday Roast", thrown.getRecipeName());
    }

    @Test
    void deletesAnUnscheduledRecipe() {
        User user = cook("rec-delete@example.com");
        Recipe recipe = recipeService.create(formFor("Toast", 1), user);

        recipeService.delete(recipe.getId(), user);

        assertTrue(recipeService.findAll(user).isEmpty());
    }

    @Test
    void detectsDuplicateNamesButAllowsRenamingItself() {
        User user = cook("rec-dupe@example.com");
        Recipe chili = recipeService.create(formFor("Chili", 6), user);

        assertTrue(recipeService.nameCollides(user, "CHILI", null));
        assertFalse(recipeService.nameCollides(user, "Chili", chili.getId()));
    }
}
