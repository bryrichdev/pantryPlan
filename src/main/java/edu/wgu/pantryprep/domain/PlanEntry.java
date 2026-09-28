package edu.wgu.pantryprep.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "plan_entries")
public class PlanEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_plan_id", nullable = false)
    private MealPlan mealPlan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_slot", nullable = false, length = 20)
    private MealSlot mealSlot = MealSlot.DINNER;

    @Column(name = "servings", nullable = false)
    private int servings = 1;

    @Column(name = "cooked", nullable = false)
    private boolean cooked;

    @Column(name = "cooked_at")
    private Instant cookedAt;

    protected PlanEntry() {
    }

    public PlanEntry(Recipe recipe, LocalDate planDate, MealSlot mealSlot, int servings) {
        this.recipe = recipe;
        this.planDate = planDate;
        this.mealSlot = mealSlot;
        this.servings = servings;
    }

    public void markCooked(Instant when) {
        if (cooked) {
            throw new IllegalStateException("Plan entry is already marked as cooked");
        }
        this.cooked = true;
        this.cookedAt = when;
    }

    public void markNotCooked() {
        this.cooked = false;
        this.cookedAt = null;
    }

    public MealPlan getMealPlan() {
        return mealPlan;
    }

    void setMealPlan(MealPlan mealPlan) {
        this.mealPlan = mealPlan;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public void setRecipe(Recipe recipe) {
        this.recipe = recipe;
    }

    public LocalDate getPlanDate() {
        return planDate;
    }

    public void setPlanDate(LocalDate planDate) {
        this.planDate = planDate;
    }

    public MealSlot getMealSlot() {
        return mealSlot;
    }

    public void setMealSlot(MealSlot mealSlot) {
        this.mealSlot = mealSlot;
    }

    public int getServings() {
        return servings;
    }

    public void setServings(int servings) {
        this.servings = servings;
    }

    public boolean isCooked() {
        return cooked;
    }

    public Instant getCookedAt() {
        return cookedAt;
    }
}
