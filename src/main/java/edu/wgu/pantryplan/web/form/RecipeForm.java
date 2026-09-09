package edu.wgu.pantryplan.web.form;

import edu.wgu.pantryplan.domain.Recipe;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Backing object for the recipe form.
 *
 * <p>Tags arrive as one comma-separated field because that is far easier to
 * type than a repeating control, and {@link #parsedTags()} turns the string
 * into the normalised set the entity stores.
 */
public class RecipeForm {

    private Long id;

    @NotBlank(message = "Enter a recipe name")
    @Size(max = 150, message = "Name must be 150 characters or fewer")
    private String name;

    @NotNull(message = "Enter how many this recipe serves")
    @Min(value = 1, message = "Servings must be at least 1")
    @Max(value = 100, message = "Servings must be 100 or fewer")
    private Integer servings = 4;

    @Min(value = 0, message = "Prep time cannot be negative")
    @Max(value = 1440, message = "Prep time must be under 24 hours")
    private Integer prepMinutes = 0;

    @Min(value = 0, message = "Cook time cannot be negative")
    @Max(value = 1440, message = "Cook time must be under 24 hours")
    private Integer cookMinutes = 0;

    @Size(max = 2000, message = "Description must be 2000 characters or fewer")
    private String description;

    @Size(max = 20000, message = "Instructions must be 20000 characters or fewer")
    private String instructions;

    @Size(max = 300, message = "Tags must be 300 characters or fewer")
    private String tagsCsv;

    public static RecipeForm from(Recipe recipe) {
        RecipeForm form = new RecipeForm();
        form.setId(recipe.getId());
        form.setName(recipe.getName());
        form.setServings(recipe.getServings());
        form.setPrepMinutes(recipe.getPrepMinutes());
        form.setCookMinutes(recipe.getCookMinutes());
        form.setDescription(recipe.getDescription());
        form.setInstructions(recipe.getInstructions());
        form.setTagsCsv(String.join(", ", recipe.getTags()));
        return form;
    }

    /**
     * Splits the comma-separated field, trims and lowercases each entry, drops
     * blanks and anything over the column length, and keeps typing order.
     */
    public Set<String> parsedTags() {
        Set<String> tags = new LinkedHashSet<>();
        if (tagsCsv == null || tagsCsv.isBlank()) {
            return tags;
        }
        for (String raw : tagsCsv.split(",")) {
            String tag = raw.trim().toLowerCase();
            if (!tag.isEmpty() && tag.length() <= 40) {
                tags.add(tag);
            }
        }
        return tags;
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

    public Integer getServings() {
        return servings;
    }

    public void setServings(Integer servings) {
        this.servings = servings;
    }

    public Integer getPrepMinutes() {
        return prepMinutes;
    }

    public void setPrepMinutes(Integer prepMinutes) {
        this.prepMinutes = prepMinutes;
    }

    public Integer getCookMinutes() {
        return cookMinutes;
    }

    public void setCookMinutes(Integer cookMinutes) {
        this.cookMinutes = cookMinutes;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getTagsCsv() {
        return tagsCsv;
    }

    public void setTagsCsv(String tagsCsv) {
        this.tagsCsv = tagsCsv;
    }
}
