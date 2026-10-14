package edu.wgu.pantryprep.web.form;

import edu.wgu.pantryprep.domain.RecipeLine;
import edu.wgu.pantryprep.domain.Unit;
import java.math.BigDecimal;

/**
 * One ingredient row on the recipe form.
 *
 * <p>No bean-validation annotations here on purpose. The form always carries at
 * least one row so there is something to type into, and a row left untouched
 * must not produce errors. {@link #isBlank()} lets the controller drop those
 * rows before it validates what remains.
 */
public class RecipeLineForm {

    private Long ingredientId;
    private BigDecimal quantity;
    private Unit unit;
    private String note;

    public RecipeLineForm() {
    }

    public static RecipeLineForm from(RecipeLine line) {
        RecipeLineForm form = new RecipeLineForm();
        form.setIngredientId(line.getIngredient().getId());
        form.setQuantity(plain(line.getQuantity()));
        form.setUnit(line.getUnit());
        form.setNote(line.getNote());
        return form;
    }

    /**
     * The database keeps three decimal places, so 16 comes back as 16.000.
     * The edit form shows what the cook typed: 16, 1.5, 0.25.
     */
    private static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    /**
     * A row nobody filled in. Dropped before validation.
     */
    public boolean isBlank() {
        return ingredientId == null
                && quantity == null
                && unit == null
                && (note == null || note.isBlank());
    }

    public Long getIngredientId() {
        return ingredientId;
    }

    public void setIngredientId(Long ingredientId) {
        this.ingredientId = ingredientId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
