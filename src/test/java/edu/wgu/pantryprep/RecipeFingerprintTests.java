package edu.wgu.pantryprep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.IngredientCategory;
import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.RecipeLine;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.service.RecipeFingerprint;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The recipe fingerprint on its own, with no database. */
class RecipeFingerprintTests {

    private static Recipe chili(String name, String cumin, String... tags) {
        Recipe recipe = new Recipe(null, name, 4);
        recipe.setInstructions("Brown the beef. Simmer an hour.");
        recipe.setTags(Set.of(tags));
        recipe.addLine(new RecipeLine(new Ingredient(null, "Ground beef", IngredientCategory.MEAT),
                new BigDecimal("1.000"), Unit.POUND));
        recipe.addLine(new RecipeLine(new Ingredient(null, "Cumin", IngredientCategory.SPICE),
                new BigDecimal(cumin), Unit.TEASPOON));
        return recipe;
    }

    @Test
    void isASha256HexString() {
        assertTrue(RecipeFingerprint.of(chili("Chili", "2")).matches("[0-9a-f]{64}"));
    }

    @Test
    void ignoresCosmeticDifferences() {
        String original = RecipeFingerprint.of(chili("Chili", "2", "dinner", "spicy"));
        assertEquals(original, RecipeFingerprint.of(chili("  chili ", "2.000", "spicy", "dinner")),
                "case, spacing, trailing zeros, and tag order do not make a new recipe");
    }

    @Test
    void changesWhenTheRecipeDoes() {
        String original = RecipeFingerprint.of(chili("Chili", "2"));
        assertNotEquals(original, RecipeFingerprint.of(chili("Chili", "3")), "a different amount");
        assertNotEquals(original, RecipeFingerprint.of(chili("Texas chili", "2")), "a different name");
        assertNotEquals(original, RecipeFingerprint.of(chili("Chili", "2", "dinner")), "an added tag");

        Recipe moreServings = chili("Chili", "2");
        moreServings.setServings(6);
        assertNotEquals(original, RecipeFingerprint.of(moreServings), "different servings");
    }

    @Test
    void fieldsCannotRunTogether() {
        Recipe a = new Recipe(null, "ab", 1);
        a.setDescription("c");
        Recipe b = new Recipe(null, "a", 1);
        b.setDescription("bc");
        assertNotEquals(RecipeFingerprint.of(a), RecipeFingerprint.of(b));
    }
}
