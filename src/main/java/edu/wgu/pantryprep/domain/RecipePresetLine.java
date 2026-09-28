package edu.wgu.pantryprep.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** An ingredient line belonging to one shared starter recipe. */
@Entity
@Table(name = "recipe_preset_lines")
public class RecipePresetLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_preset_id", nullable = false)
    private RecipePreset recipePreset;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_preset_id", nullable = false)
    private IngredientPreset ingredientPreset;
    @Column(name = "quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;
    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 20)
    private Unit unit;
    @Column(name = "note", length = 255)
    private String note;

    protected RecipePresetLine() { }
    public IngredientPreset getIngredientPreset() { return ingredientPreset; }
    public BigDecimal getQuantity() { return quantity; }
    public Unit getUnit() { return unit; }
    public String getNote() { return note; }
}
