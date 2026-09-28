package edu.wgu.pantryprep.service;

/**
 * Raised when an ingredient cannot be deleted because other records still
 * reference it. Carries the reason so the interface can explain what to do.
 */
public class IngredientInUseException extends RuntimeException {

    private final String ingredientName;

    public IngredientInUseException(String ingredientName, String message) {
        super(message);
        this.ingredientName = ingredientName;
    }

    public String getIngredientName() {
        return ingredientName;
    }
}
