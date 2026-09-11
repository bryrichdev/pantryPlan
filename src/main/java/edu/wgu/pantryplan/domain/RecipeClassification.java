package edu.wgu.pantryplan.domain;

import java.util.List;

/**
 * The fixed lists a recipe's meal type and cuisine are chosen from.
 *
 * <p>Both are stored as text, but only these values are accepted: the form
 * offers them as dropdowns, the controller rejects anything else, and V11 adds
 * matching CHECK constraints in the database. Adding a value means adding it
 * here and in a new migration that widens the constraint.
 *
 * <p>Meal types are exactly the meal plan slots, so auto-fill can match every
 * typed recipe to a slot.
 */
public final class RecipeClassification {

    public static final List<String> MEAL_TYPES = List.of("Breakfast", "Lunch", "Dinner", "Snack");

    public static final List<String> CUISINES = List.of(
            "American", "Asian", "British", "Chinese", "French", "Greek",
            "Indian", "Italian", "Japanese", "Korean", "Mediterranean",
            "Mexican", "Middle Eastern", "Thai", "Vietnamese", "Other");

    private RecipeClassification() {
    }

    /** Blank is allowed: a recipe does not have to be classified. */
    public static boolean isAllowedMealType(String value) {
        return value == null || value.isBlank() || MEAL_TYPES.contains(value);
    }

    /** Blank is allowed: a recipe does not have to be classified. */
    public static boolean isAllowedCuisine(String value) {
        return value == null || value.isBlank() || CUISINES.contains(value);
    }
}
