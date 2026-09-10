package edu.wgu.pantryplan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * A common ingredient with its published density, shared by every account.
 *
 * <p>Reference data seeded by migration, not something any cook owns. An
 * account copies from this catalogue into its own ingredient list, and the copy
 * is then free to be edited or deleted without affecting anyone else.
 *
 * <p>Read only in the application: there are no setters, and rows only change
 * by a later migration.
 */
@Entity
@Table(name = "ingredient_presets")
public class IngredientPreset extends BaseEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private IngredientCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_unit", nullable = false, length = 20)
    private Unit stockUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_location", nullable = false, length = 30)
    private StorageLocation defaultLocation;

    @Column(name = "grams_per_cup", precision = 10, scale = 3)
    private BigDecimal gramsPerCup;

    protected IngredientPreset() {
    }

    public String getName() {
        return name;
    }

    public IngredientCategory getCategory() {
        return category;
    }

    public Unit getStockUnit() {
        return stockUnit;
    }

    public StorageLocation getDefaultLocation() {
        return defaultLocation;
    }

    public BigDecimal getGramsPerCup() {
        return gramsPerCup;
    }
}
