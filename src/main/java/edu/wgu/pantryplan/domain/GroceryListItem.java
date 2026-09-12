package edu.wgu.pantryplan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "grocery_list_items")
public class GroceryListItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grocery_list_id", nullable = false)
    private GroceryList groceryList;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(name = "needed_quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal neededQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 20)
    private Unit unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private IngredientCategory category = IngredientCategory.OTHER;

    @Column(name = "purchased", nullable = false)
    private boolean purchased;

    /**
     * True when the recipe's unit could not be converted into the ingredient's
     * stock unit. The quantity is then in the recipe's unit and the pantry was
     * not subtracted, so the cook has to check the shelf by eye. Set only at
     * construction: a line cannot become convertible later.
     */
    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    /**
     * When this line was added to the pantry, or null if it has not been.
     * Ticking an item only means it is in the trolley; this is the later step
     * of putting it away, and it keeps a line from being stocked twice.
     */
    @Column(name = "stocked_at")
    private Instant stockedAt;

    protected GroceryListItem() {
    }

    public GroceryListItem(Ingredient ingredient, BigDecimal neededQuantity, Unit unit,
                           IngredientCategory category) {
        this.ingredient = ingredient;
        this.neededQuantity = neededQuantity;
        this.unit = unit;
        this.category = category;
    }

    /**
     * A line the list could not reconcile with the pantry.
     *
     * @param amount how much the recipes call for, in the recipe's own unit
     */
    public static GroceryListItem needingReview(Ingredient ingredient, BigDecimal amount, Unit recipeUnit,
                                                IngredientCategory category) {
        GroceryListItem item = new GroceryListItem(ingredient, amount, recipeUnit, category);
        item.needsReview = true;
        return item;
    }

    public void togglePurchased() {
        this.purchased = !this.purchased;
    }

    /**
     * Records that this line went onto a shelf.
     *
     * @throws IllegalStateException if it has already been stocked, so a
     *     resubmitted form cannot add the same shopping to the pantry twice
     */
    public void markStocked(Instant when) {
        if (stockedAt != null) {
            throw new IllegalStateException("Already added to the pantry");
        }
        this.stockedAt = when;
    }

    public boolean isStocked() {
        return stockedAt != null;
    }

    /** Bought, but not yet put away. */
    public boolean isReadyToStock() {
        return purchased && stockedAt == null;
    }

    public GroceryList getGroceryList() {
        return groceryList;
    }

    void setGroceryList(GroceryList groceryList) {
        this.groceryList = groceryList;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public void setIngredient(Ingredient ingredient) {
        this.ingredient = ingredient;
    }

    public BigDecimal getNeededQuantity() {
        return neededQuantity;
    }

    public void setNeededQuantity(BigDecimal neededQuantity) {
        this.neededQuantity = neededQuantity;
    }

    public Unit getUnit() {
        return unit;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public IngredientCategory getCategory() {
        return category;
    }

    public void setCategory(IngredientCategory category) {
        this.category = category;
    }

    public boolean isPurchased() {
        return purchased;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    public Instant getStockedAt() {
        return stockedAt;
    }
}
