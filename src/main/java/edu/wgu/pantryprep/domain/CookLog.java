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
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "cook_logs")
public class CookLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_entry_id", nullable = false)
    private PlanEntry planEntry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(name = "quantity_deducted", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantityDeducted;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 20)
    private Unit unit;

    @Column(name = "cooked_at", nullable = false)
    private Instant cookedAt;

    @Column(name = "reversed", nullable = false)
    private boolean reversed;

    /* The pantry row the stock came from, so undo can put it back. The id is a
       plain value rather than a relationship: cooking deletes a row it
       empties, and the database then clears this column. The location and
       dates are copied so undo can recreate that row. All four are null on
       logs written before V17. */
    @Column(name = "pantry_item_id")
    private Long sourcePantryItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_location", length = 30)
    private StorageLocation sourceLocation;

    @Column(name = "source_purchased_on")
    private LocalDate sourcePurchasedOn;

    @Column(name = "source_expires_on")
    private LocalDate sourceExpiresOn;

    protected CookLog() {
    }

    public CookLog(User user, PlanEntry planEntry, Ingredient ingredient,
                   BigDecimal quantityDeducted, Unit unit, Instant cookedAt) {
        this.user = user;
        this.planEntry = planEntry;
        this.ingredient = ingredient;
        this.quantityDeducted = quantityDeducted;
        this.unit = unit;
        this.cookedAt = cookedAt;
    }

    /** A deduction from one pantry row, remembering that row for undo. */
    public CookLog(User user, PlanEntry planEntry, Ingredient ingredient, PantryItem source,
                   BigDecimal quantityDeducted, Unit unit, Instant cookedAt) {
        this(user, planEntry, ingredient, quantityDeducted, unit, cookedAt);
        this.sourcePantryItemId = source.getId();
        this.sourceLocation = source.getLocation();
        this.sourcePurchasedOn = source.getPurchasedOn();
        this.sourceExpiresOn = source.getExpiresOn();
    }

    /**
     * The source row is about to be deleted because this deduction emptied it.
     * Its id is dropped so the log never points at a missing row; the location
     * and dates stay, which is what undo needs to recreate it.
     */
    public void sourceRowRemoved() {
        this.sourcePantryItemId = null;
    }

    /** False for logs written before cook logs recorded their source row. */
    public boolean hasSource() {
        return sourceLocation != null;
    }

    public void markReversed() {
        this.reversed = true;
    }

    public User getUser() {
        return user;
    }

    public PlanEntry getPlanEntry() {
        return planEntry;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public BigDecimal getQuantityDeducted() {
        return quantityDeducted;
    }

    public Unit getUnit() {
        return unit;
    }

    public Instant getCookedAt() {
        return cookedAt;
    }

    public boolean isReversed() {
        return reversed;
    }

    public Long getSourcePantryItemId() {
        return sourcePantryItemId;
    }

    public StorageLocation getSourceLocation() {
        return sourceLocation;
    }

    public LocalDate getSourcePurchasedOn() {
        return sourcePurchasedOn;
    }

    public LocalDate getSourceExpiresOn() {
        return sourceExpiresOn;
    }
}
