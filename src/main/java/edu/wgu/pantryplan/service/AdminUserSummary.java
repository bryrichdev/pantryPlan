package edu.wgu.pantryplan.service;

import java.time.Instant;

/**
 * One row of the admin user list. A plain class with getters, since Thymeleaf
 * reads properties through getters.
 */
public class AdminUserSummary {

    private final Long id;
    private final String displayName;
    private final String email;
    private final boolean admin;
    private final boolean enabled;
    private final Instant joinedAt;
    private final long recipeCount;
    private final long pantryItemCount;
    private final long mealPlanCount;

    public AdminUserSummary(Long id, String displayName, String email, boolean admin, boolean enabled,
                            Instant joinedAt, long recipeCount, long pantryItemCount, long mealPlanCount) {
        this.id = id;
        this.displayName = displayName;
        this.email = email;
        this.admin = admin;
        this.enabled = enabled;
        this.joinedAt = joinedAt;
        this.recipeCount = recipeCount;
        this.pantryItemCount = pantryItemCount;
        this.mealPlanCount = mealPlanCount;
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public boolean isAdmin() {
        return admin;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public long getRecipeCount() {
        return recipeCount;
    }

    public long getPantryItemCount() {
        return pantryItemCount;
    }

    public long getMealPlanCount() {
        return mealPlanCount;
    }
}
