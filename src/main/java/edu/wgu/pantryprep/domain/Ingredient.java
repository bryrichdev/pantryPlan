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

@Entity
@Table(name = "ingredients")
public class Ingredient extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private IngredientCategory category = IngredientCategory.OTHER;

    /**
     * The unit this ingredient is kept in. Every pantry row for it uses this,
     * so adding up what is on hand is plain addition rather than a conversion
     * per row. Recipe lines keep their own unit, since a recipe may call for
     * cups of something stocked in grams.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "stock_unit", nullable = false, length = 20)
    private Unit stockUnit = Unit.GRAM;

    /**
     * Where this usually lives. Only the starting choice on a new pantry row —
     * the row can be stored anywhere, so a backup block of butter in the freezer
     * stays distinct from the one in the fridge.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "default_location", nullable = false, length = 30)
    private StorageLocation defaultLocation = StorageLocation.PANTRY;

    /**
     * How much a cook usually stocks at once, in the stock unit. Like the
     * default location, it only prefills a new pantry row. Null when there is
     * no usual amount, which leaves the pantry amount blank.
     */
    @Column(name = "default_quantity", precision = 10, scale = 3)
    private BigDecimal defaultQuantity;

    @Column(name = "grams_per_cup", precision = 10, scale = 3)
    private BigDecimal gramsPerCup;

    protected Ingredient() {
    }

    public Ingredient(User user, String name, IngredientCategory category) {
        this.user = user;
        this.name = name;
        this.category = category;
    }

    public boolean hasVolumeWeightRatio() {
        return gramsPerCup != null && gramsPerCup.signum() > 0;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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

    public Unit getStockUnit() {
        return stockUnit;
    }

    public void setStockUnit(Unit stockUnit) {
        this.stockUnit = stockUnit;
    }

    public StorageLocation getDefaultLocation() {
        return defaultLocation;
    }

    public void setDefaultLocation(StorageLocation defaultLocation) {
        this.defaultLocation = defaultLocation;
    }

    public BigDecimal getDefaultQuantity() {
        return defaultQuantity;
    }

    public void setDefaultQuantity(BigDecimal defaultQuantity) {
        this.defaultQuantity = defaultQuantity;
    }

    public BigDecimal getGramsPerCup() {
        return gramsPerCup;
    }

    public void setGramsPerCup(BigDecimal gramsPerCup) {
        this.gramsPerCup = gramsPerCup;
    }
}
