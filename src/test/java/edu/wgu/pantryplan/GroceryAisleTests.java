package edu.wgu.pantryplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryplan.domain.GroceryListItem;
import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.IngredientCategory;
import edu.wgu.pantryplan.domain.Unit;
import edu.wgu.pantryplan.web.GroceryAisle;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Plain unit tests: no Spring context and no database. Grouping is pure logic
 * over objects already in memory, so there is nothing to start up.
 */
class GroceryAisleTests {

    private static GroceryListItem item(String name, IngredientCategory category) {
        Ingredient ingredient = new Ingredient(null, name, category);
        return new GroceryListItem(ingredient, BigDecimal.ONE, Unit.GRAM, category);
    }

    @Test
    void groupsInCategoryDeclarationOrder() {
        List<GroceryAisle> aisles = GroceryAisle.group(List.of(
                item("Cumin", IngredientCategory.SPICE),
                item("Milk", IngredientCategory.DAIRY),
                item("Onion", IngredientCategory.PRODUCE)));

        assertEquals(3, aisles.size());
        assertEquals(IngredientCategory.PRODUCE, aisles.get(0).getCategory(), "produce comes first");
        assertEquals(IngredientCategory.DAIRY, aisles.get(1).getCategory());
        assertEquals(IngredientCategory.SPICE, aisles.get(2).getCategory());
    }

    @Test
    void sortsEachAisleByNameIgnoringCase() {
        List<GroceryAisle> aisles = GroceryAisle.group(List.of(
                item("carrot", IngredientCategory.PRODUCE),
                item("Basil", IngredientCategory.PRODUCE),
                item("Apple", IngredientCategory.PRODUCE)));

        List<GroceryListItem> produce = aisles.get(0).getItems();
        assertEquals("Apple", produce.get(0).getIngredient().getName());
        assertEquals("Basil", produce.get(1).getIngredient().getName());
        assertEquals("carrot", produce.get(2).getIngredient().getName(),
                "a lowercase name is not pushed after every capitalised one");
    }

    @Test
    void putsAFlaggedRowAfterTheNormalRowForTheSameIngredient() {
        Ingredient basil = new Ingredient(null, "Basil", IngredientCategory.PRODUCE);
        GroceryListItem flagged = GroceryListItem.needingReview(
                basil, new BigDecimal("2"), Unit.CUP, IngredientCategory.PRODUCE);
        GroceryListItem normal = new GroceryListItem(
                basil, new BigDecimal("30"), Unit.GRAM, IngredientCategory.PRODUCE);

        List<GroceryListItem> produce = GroceryAisle.group(List.of(flagged, normal)).get(0).getItems();

        assertFalse(produce.get(0).isNeedsReview());
        assertTrue(produce.get(1).isNeedsReview());
    }

    @Test
    void anEmptyListHasNoAisles() {
        assertTrue(GroceryAisle.group(List.of()).isEmpty());
    }
}
