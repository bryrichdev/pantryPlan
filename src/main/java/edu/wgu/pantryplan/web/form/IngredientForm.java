package edu.wgu.pantryplan.web.form;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.IngredientCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Backing object for the ingredient form.
 */
public class IngredientForm {

    private Long id;

    @NotBlank(message = "Enter an ingredient name")
    @Size(max = 120, message = "Name must be 120 characters or fewer")
    private String name;

    @NotNull(message = "Choose a category")
    private IngredientCategory category = IngredientCategory.OTHER;

    @DecimalMin(value = "0.001", message = "Weight per cup must be greater than zero")
    @Digits(integer = 7, fraction = 3, message = "Use up to three decimal places")
    private BigDecimal gramsPerCup;

    public static IngredientForm from(Ingredient ingredient) {
        IngredientForm form = new IngredientForm();
        form.setId(ingredient.getId());
        form.setName(ingredient.getName());
        form.setCategory(ingredient.getCategory());
        form.setGramsPerCup(ingredient.getGramsPerCup());
        return form;
    }

    public boolean isNew() {
        return id == null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public IngredientCategory getCategory() {
        return category;
    }

    public void setCategory(IngredientCategory category) {
        this.category = category;
    }

    public BigDecimal getGramsPerCup() {
        return gramsPerCup;
    }

    public void setGramsPerCup(BigDecimal gramsPerCup) {
        this.gramsPerCup = gramsPerCup;
    }
}
