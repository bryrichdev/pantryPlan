package edu.wgu.pantryplan.service;

/**
 * Raised when a recipe cannot be deleted because a meal plan still uses it.
 */
public class RecipeInUseException extends RuntimeException {

    private final String recipeName;

    public RecipeInUseException(String recipeName, String message) {
        super(message);
        this.recipeName = recipeName;
    }

    public String getRecipeName() {
        return recipeName;
    }
}
