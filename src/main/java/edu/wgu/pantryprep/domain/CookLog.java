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
}
