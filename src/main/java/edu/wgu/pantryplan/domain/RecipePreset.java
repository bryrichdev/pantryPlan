package edu.wgu.pantryplan.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Shared, read-only starter recipe catalogue copied into individual accounts. */
@Entity
@Table(name = "recipe_presets")
public class RecipePreset extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;
    @Column(name = "description", columnDefinition = "text")
    private String description;
    @Column(name = "meal_type", nullable = false, length = 40)
    private String mealType;
    @Column(name = "nationality", nullable = false, length = 60)
    private String nationality;
    @Column(name = "servings", nullable = false)
    private int servings;
    @Column(name = "prep_minutes", nullable = false)
    private int prepMinutes;
    @Column(name = "cook_minutes", nullable = false)
    private int cookMinutes;
    @Column(name = "instructions", columnDefinition = "text")
    private String instructions;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "recipe_preset_tags", joinColumns = @JoinColumn(name = "recipe_preset_id"))
    @Column(name = "tag", nullable = false, length = 40)
    private Set<String> tags = new LinkedHashSet<>();
    @OneToMany(mappedBy = "recipePreset", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RecipePresetLine> lines = new ArrayList<>();

    protected RecipePreset() { }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getMealType() { return mealType; }
    public String getNationality() { return nationality; }
    public int getServings() { return servings; }
    public int getPrepMinutes() { return prepMinutes; }
    public int getCookMinutes() { return cookMinutes; }
    public String getInstructions() { return instructions; }
    public Set<String> getTags() { return tags; }
    public List<RecipePresetLine> getLines() { return Collections.unmodifiableList(lines); }
}
