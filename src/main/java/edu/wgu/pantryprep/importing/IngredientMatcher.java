package edu.wgu.pantryprep.importing;

import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.IngredientCategory;
import edu.wgu.pantryprep.domain.StorageLocation;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.repository.IngredientRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finds a row's ingredient among the cook's own by name, ignoring case and
 * extra spaces, and plans a new one for a name that doesn't match. A planned
 * ingredient is a real but unsaved {@link Ingredient}, so conversions can use
 * it straight away; it takes its category and unit from the first row that
 * names it, and nothing is written until {@link #saveNew}.
 */
final class IngredientMatcher {

    private final User user;
    private final Map<String, Ingredient> existing = new LinkedHashMap<>();
    private final Map<String, Ingredient> planned = new LinkedHashMap<>();

    IngredientMatcher(User user, List<Ingredient> ingredients) {
        this.user = user;
        ingredients.forEach(ingredient -> existing.put(key(ingredient.getName()), ingredient));
    }

    static String key(String name) {
        return tidy(name).toLowerCase(Locale.ROOT);
    }

    static String tidy(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    /** The cook's ingredient, or one already planned, with this name; null when neither. */
    Ingredient find(String name) {
        Ingredient found = existing.get(key(name));
        return found != null ? found : planned.get(key(name));
    }

    boolean isNew(Ingredient ingredient) {
        return ingredient.getId() == null;
    }

    /** Plans a new ingredient, or returns the one already planned under this name. */
    Ingredient plan(String name, IngredientCategory category, Unit unit) {
        return planned.computeIfAbsent(key(name), ignored -> {
            IngredientCategory kind = category == null ? IngredientCategory.OTHER : category;
            Ingredient ingredient = new Ingredient(user, tidy(name), kind);
            ingredient.setStockUnit(unit == null ? Unit.GRAM : unit);
            ingredient.setDefaultLocation(usualPlaceFor(kind));
            return ingredient;
        });
    }

    List<String> plannedNames() {
        return planned.values().stream().map(Ingredient::getName).toList();
    }

    /** Saves every planned ingredient, which gives each its id. */
    void saveNew(IngredientRepository repository) {
        repository.saveAll(planned.values());
        repository.flush();
    }

    /** Where a new ingredient usually lives, from what kind of thing it is. */
    static StorageLocation usualPlaceFor(IngredientCategory category) {
        return switch (category) {
            case PRODUCE, DAIRY, MEAT -> StorageLocation.FRIDGE;
            case FROZEN -> StorageLocation.FREEZER;
            case SPICE -> StorageLocation.SPICE_RACK;
            default -> StorageLocation.PANTRY;
        };
    }
}
