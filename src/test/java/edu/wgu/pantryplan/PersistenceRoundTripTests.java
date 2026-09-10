package edu.wgu.pantryplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.IngredientCategory;
import edu.wgu.pantryplan.domain.PantryItem;
import edu.wgu.pantryplan.domain.Recipe;
import edu.wgu.pantryplan.domain.RecipeLine;
import edu.wgu.pantryplan.domain.Unit;
import edu.wgu.pantryplan.domain.UnitConversion;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.repository.IngredientRepository;
import edu.wgu.pantryplan.repository.PantryItemRepository;
import edu.wgu.pantryplan.repository.RecipeLineRepository;
import edu.wgu.pantryplan.repository.RecipeRepository;
import edu.wgu.pantryplan.repository.UnitConversionRepository;
import edu.wgu.pantryplan.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the JPA mappings actually persist and reload, which schema
 * validation alone does not cover: bidirectional wiring, cascades,
 * orphan removal, element collections, and the derived query methods.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PersistenceRoundTripTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private RecipeLineRepository recipeLineRepository;

    @Autowired
    private PantryItemRepository pantryItemRepository;

    @Autowired
    private UnitConversionRepository unitConversionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private User newUser(String email) {
        return userRepository.save(new User(email, "$2a$10$fakehashfortestingonly", "Test Cook"));
    }

    @Test
    void savesAndReloadsUserWithTimestamps() {
        User saved = newUser("roundtrip@example.com");
        entityManager.flush();

        assertNotNull(saved.getId(), "id should be assigned by the database");
        assertNotNull(saved.getCreatedAt(), "@PrePersist should set createdAt");
        assertNotNull(saved.getUpdatedAt(), "@PrePersist should set updatedAt");

        Optional<User> found = userRepository.findByEmailIgnoreCase("ROUNDTRIP@EXAMPLE.COM");
        assertTrue(found.isPresent(), "case-insensitive lookup should find the user");
        assertEquals("Test Cook", found.get().getDisplayName());
    }

    @Test
    void cascadesRecipeLinesAndTagsFromParent() {
        User user = newUser("cascade@example.com");
        Ingredient flour = ingredientRepository.save(
                new Ingredient(user, "All-purpose flour", IngredientCategory.PANTRY_STAPLE));

        Recipe recipe = new Recipe(user, "Test Bread", 4);
        recipe.setTags(java.util.Set.of("baking", "weeknight"));
        recipe.addLine(new RecipeLine(flour, new BigDecimal("2.000"), Unit.CUP));

        Recipe saved = recipeRepository.save(recipe);
        entityManager.flush();
        entityManager.clear();

        Recipe reloaded = recipeRepository.findById(saved.getId()).orElseThrow();
        assertEquals(1, reloaded.getLines().size(), "line should cascade from the recipe");
        assertEquals(2, reloaded.getTags().size(), "tags should persist to recipe_tags");
        assertEquals(flour.getId(), reloaded.getLines().get(0).getIngredient().getId());
        assertEquals(reloaded.getId(), reloaded.getLines().get(0).getRecipe().getId(),
                "addLine should have set the owning side");
    }

    @Test
    void removesOrphanedRecipeLines() {
        User user = newUser("orphan@example.com");
        Ingredient sugar = ingredientRepository.save(
                new Ingredient(user, "Sugar", IngredientCategory.PANTRY_STAPLE));

        Recipe recipe = new Recipe(user, "Simple Syrup", 2);
        recipe.addLine(new RecipeLine(sugar, new BigDecimal("1.000"), Unit.CUP));
        Recipe saved = recipeRepository.save(recipe);
        entityManager.flush();

        assertEquals(1, recipeLineRepository.findAllByRecipe(saved).size());

        saved.clearLines();
        recipeRepository.save(saved);
        entityManager.flush();
        entityManager.clear();

        Recipe reloaded = recipeRepository.findById(saved.getId()).orElseThrow();
        assertTrue(reloaded.getLines().isEmpty(), "orphanRemoval should delete the line row");
        assertFalse(recipeLineRepository.existsByIngredient(sugar));
    }

    @Test
    void scalesRecipeLineQuantityToPlannedServings() {
        User user = newUser("scale@example.com");
        Ingredient rice = ingredientRepository.save(
                new Ingredient(user, "Rice", IngredientCategory.PANTRY_STAPLE));

        Recipe recipe = new Recipe(user, "Rice Bowl", 4);
        RecipeLine line = new RecipeLine(rice, new BigDecimal("2.000"), Unit.CUP);
        recipe.addLine(line);
        recipeRepository.save(recipe);
        entityManager.flush();

        assertEquals(0, new BigDecimal("3").compareTo(line.scaledTo(6)),
                "2 cups for 4 servings should scale to 3 cups for 6");
        assertEquals(0, new BigDecimal("2.000").compareTo(line.scaledTo(4)),
                "matching servings should return the original quantity");
    }

    @Test
    void searchesRecipesByNameIngredientAndTag() {
        User user = newUser("search@example.com");
        Ingredient basil = ingredientRepository.save(
                new Ingredient(user, "Basil", IngredientCategory.PRODUCE));

        Recipe pesto = new Recipe(user, "Pesto Pasta", 4);
        pesto.setTags(java.util.Set.of("italian"));
        pesto.addLine(new RecipeLine(basil, new BigDecimal("1.000"), Unit.CUP));
        recipeRepository.save(pesto);

        Recipe toast = new Recipe(user, "Avocado Toast", 1);
        recipeRepository.save(toast);
        entityManager.flush();
        entityManager.clear();

        assertEquals(2, recipeRepository.search(user, "", "", "").size(),
                "blank criteria should return everything");
        assertEquals(1, recipeRepository.search(user, "pesto", "", "").size());
        assertEquals(1, recipeRepository.search(user, "", "basil", "").size());
        assertEquals(1, recipeRepository.search(user, "", "", "italian").size());
        assertTrue(recipeRepository.search(user, "nonexistent", "", "").isEmpty());
    }

    @Test
    void deductsAndRestoresPantryQuantity() {
        User user = newUser("pantry@example.com");
        Ingredient oats = ingredientRepository.save(
                new Ingredient(user, "Oats", IngredientCategory.PANTRY_STAPLE));

        PantryItem item = pantryItemRepository.save(
                new PantryItem(user, oats, new BigDecimal("500.000")));
        entityManager.flush();

        item.deduct(new BigDecimal("120.000"));
        assertEquals(0, new BigDecimal("380.000").compareTo(item.getQuantity()));

        item.deduct(new BigDecimal("9999.000"));
        assertEquals(0, BigDecimal.ZERO.compareTo(item.getQuantity()),
                "deduct should floor at zero, never go negative");

        item.restore(new BigDecimal("50.000"));
        assertEquals(0, new BigDecimal("50.000").compareTo(item.getQuantity()));
    }

    @Test
    void seedsTenUnitConversions() {
        List<UnitConversion> all = unitConversionRepository.findAll();
        assertEquals(10, all.size(), "V3 migration should seed ten conversion rows");

        UnitConversion cup = unitConversionRepository
                .findByFromUnitAndToUnit(Unit.CUP, Unit.MILLILITER)
                .orElseThrow();
        assertEquals(0, new BigDecimal("236.588236500").compareTo(cup.getFactor()));
        assertEquals(0, new BigDecimal("473.176473").compareTo(cup.apply(new BigDecimal("2"))));
    }
}
