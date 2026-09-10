package edu.wgu.pantryplan.web.form;

import edu.wgu.pantryplan.domain.MealPlan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Backing object for creating or renaming a meal plan.
 */
public class MealPlanForm {

    private Long id;

    @NotBlank(message = "Give the plan a name")
    @Size(max = 120, message = "Name must be 120 characters or fewer")
    private String name;

    @NotNull(message = "Choose the week this plan starts")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate weekStartDate;

    public static MealPlanForm from(MealPlan plan) {
        MealPlanForm form = new MealPlanForm();
        form.setId(plan.getId());
        form.setName(plan.getName());
        form.setWeekStartDate(plan.getWeekStartDate());
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

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public void setWeekStartDate(LocalDate weekStartDate) {
        this.weekStartDate = weekStartDate;
    }
}
