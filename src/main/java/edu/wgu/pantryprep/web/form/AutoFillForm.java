package edu.wgu.pantryprep.web.form;

import edu.wgu.pantryprep.domain.MealSlot;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Options for filling a week automatically.
 */
public class AutoFillForm {

    @NotEmpty(message = "Choose at least one meal to fill")
    private Set<MealSlot> slots = new LinkedHashSet<>(Set.of(MealSlot.DINNER));

    @NotNull(message = "Enter how many you are cooking for")
    @Min(value = 1, message = "Servings must be at least 1")
    @Max(value = 100, message = "Servings must be 100 or fewer")
    private Integer servings = 4;

    /** Rank candidates by how much of each the pantry already covers. */
    private boolean favorPantry = true;

    /** Work through every recipe before any repeats. */
    private boolean avoidRepeats = true;

    /** Replace meals already scheduled rather than only filling gaps. */
    private boolean replaceExisting;

    public Set<MealSlot> getSlots() {
        return slots;
    }

    public void setSlots(Set<MealSlot> slots) {
        this.slots = slots == null ? new LinkedHashSet<>() : new LinkedHashSet<>(slots);
    }

    public Integer getServings() {
        return servings;
    }

    public void setServings(Integer servings) {
        this.servings = servings;
    }

    public boolean isFavorPantry() {
        return favorPantry;
    }

    public void setFavorPantry(boolean favorPantry) {
        this.favorPantry = favorPantry;
    }

    public boolean isAvoidRepeats() {
        return avoidRepeats;
    }

    public void setAvoidRepeats(boolean avoidRepeats) {
        this.avoidRepeats = avoidRepeats;
    }

    public boolean isReplaceExisting() {
        return replaceExisting;
    }

    public void setReplaceExisting(boolean replaceExisting) {
        this.replaceExisting = replaceExisting;
    }
}
