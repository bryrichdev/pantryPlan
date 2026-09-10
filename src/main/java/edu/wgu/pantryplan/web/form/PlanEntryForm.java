package edu.wgu.pantryplan.web.form;

import edu.wgu.pantryplan.domain.MealSlot;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Backing object for dropping one recipe onto one meal of one day.
 */
public class PlanEntryForm {

    @NotNull(message = "Choose a recipe")
    private Long recipeId;

    @NotNull(message = "Choose a day")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate planDate;

    @NotNull(message = "Choose a meal")
    private MealSlot mealSlot = MealSlot.DINNER;

    @NotNull(message = "Enter how many you are cooking for")
    @Min(value = 1, message = "Servings must be at least 1")
    @Max(value = 100, message = "Servings must be 100 or fewer")
    private Integer servings = 4;

    public Long getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(Long recipeId) {
        this.recipeId = recipeId;
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

    public Integer getServings() {
        return servings;
    }

    public void setServings(Integer servings) {
        this.servings = servings;
    }
}
