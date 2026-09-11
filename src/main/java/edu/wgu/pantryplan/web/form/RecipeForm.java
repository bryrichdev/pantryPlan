package edu.wgu.pantryplan.web.form;

import edu.wgu.pantryplan.domain.Recipe;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Backing object for the recipe form.
 *
 * <p>Tags arrive as one comma-separated field because that is far easier to
 * type than a repeating control. Ingredient lines arrive as an indexed list —
 * {@code lines[0].quantity}, {@code lines[1].quantity} and so on — which Spring
 * grows automatically as it binds, so the browser can add rows without the
 * server knowing in advance how many there will be.
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

    @Size(max = 40, message = "Meal type must be 40 characters or fewer")
    private String mealType;

    @Size(max = 60, message = "Nationality must be 60 characters or fewer")
    private String nationality;

    @Size(max = 20000, message = "Instructions must be 20000 characters or fewer")
    private String instructions;

    @Size(max = 300, message = "Tags must be 300 characters or fewer")
    private String tagsCsv;

    private List<RecipeLineForm> lines = new ArrayList<>();

    public static RecipeForm from(Recipe recipe) {
        RecipeForm form = new RecipeForm();
        form.setId(recipe.getId());
        form.setName(recipe.getName());
        form.setServings(recipe.getServings());
        form.setPrepMinutes(recipe.getPrepMinutes());
        form.setCookMinutes(recipe.getCookMinutes());
        form.setDescription(recipe.getDescription());
        form.setMealType(recipe.getMealType());
        form.setNationality(recipe.getNationality());
        form.setInstructions(recipe.getInstructions());
        form.setTagsCsv(String.join(", ", recipe.getTags()));
        recipe.getLines().forEach(line -> form.getLines().add(RecipeLineForm.from(line)));
        form.ensureOneEmptyRow();
        return form;
    }

    /**
     * Guarantees the form renders with something to type into.
     */
    public void ensureOneEmptyRow() {
        if (lines.isEmpty()) {
            lines.add(new RecipeLineForm());
        }
    }

    /**
     * Discards rows nobody touched, so an untouched trailing row never becomes
     * a validation error.
     */
    public void removeBlankLines() {
        lines.removeIf(RecipeLineForm::isBlank);
    }

    /**
     * Splits the comma-separated tag field, trims and lowercases each entry,
     * drops blanks and anything over the column length, and keeps typing order.
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

    public String getMealType() {
        return mealType;
    }

    public void setMealType(String mealType) {
        this.mealType = mealType;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
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

    public List<RecipeLineForm> getLines() {
        return lines;
    }

    public void setLines(List<RecipeLineForm> lines) {
        this.lines = lines == null ? new ArrayList<>() : lines;
    }
}
