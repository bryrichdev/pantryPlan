package edu.wgu.pantryplan.web;

import edu.wgu.pantryplan.domain.GroceryListItem;
import edu.wgu.pantryplan.domain.IngredientCategory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One category of a grocery list, as the detail page shows it.
 *
 * <p>A plain class rather than a record because Thymeleaf reads properties
 * through getters.
 */
public class GroceryAisle {

    /**
     * Alphabetical by ingredient, ignoring case. When one ingredient has both
     * a normal row and a flagged row, the normal row comes first.
     */
    private static final Comparator<GroceryListItem> SHELF_ORDER =
            Comparator.comparing((GroceryListItem item) -> item.getIngredient().getName(),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(GroceryListItem::isNeedsReview);

    private final IngredientCategory category;
    private final List<GroceryListItem> items;

    public GroceryAisle(IngredientCategory category, List<GroceryListItem> items) {
        this.category = category;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    /**
     * Groups items by category. Aisles come out in the order the categories are
     * declared, which runs roughly the way a store is walked: produce first,
     * then dairy and meat, then the shelves. An EnumMap iterates in that order
     * without any sorting.
     */
    public static List<GroceryAisle> group(Collection<GroceryListItem> items) {
        Map<IngredientCategory, List<GroceryListItem>> byCategory = new EnumMap<>(IngredientCategory.class);
        for (GroceryListItem item : items) {
            byCategory.computeIfAbsent(item.getCategory(), key -> new ArrayList<>()).add(item);
        }

        List<GroceryAisle> aisles = new ArrayList<>();
        byCategory.forEach((category, inAisle) -> {
            inAisle.sort(SHELF_ORDER);
            aisles.add(new GroceryAisle(category, inAisle));
        });
        return aisles;
    }

    public IngredientCategory getCategory() {
        return category;
    }

    public List<GroceryListItem> getItems() {
        return items;
    }
}
