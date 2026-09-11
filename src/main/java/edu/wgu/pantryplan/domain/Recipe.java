package edu.wgu.pantryplan.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "recipes")
public class Recipe extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "meal_type", length = 40)
    private String mealType;

    @Column(name = "nationality", length = 60)
    private String nationality;

    @Column(name = "servings", nullable = false)
    private int servings = 1;

    @Column(name = "prep_minutes", nullable = false)
    private int prepMinutes;

    @Column(name = "cook_minutes", nullable = false)
    private int cookMinutes;

    @Column(name = "instructions", columnDefinition = "text")
    private String instructions;

    @Column(name = "times_cooked", nullable = false)
    private int timesCooked;

    @Column(name = "last_cooked_at")
    private Instant lastCookedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "recipe_tags", joinColumns = @JoinColumn(name = "recipe_id"))
    @Column(name = "tag", nullable = false, length = 40)
    private Set<String> tags = new LinkedHashSet<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RecipeLine> lines = new ArrayList<>();

    protected Recipe() {
    }

    public Recipe(User user, String name, int servings) {
        this.user = user;
        this.name = name;
        this.servings = servings;
    }

    public void addLine(RecipeLine line) {
        lines.add(line);
        line.setRecipe(this);
    }

    public void removeLine(RecipeLine line) {
        lines.remove(line);
        line.setRecipe(null);
    }

    public void clearLines() {
        for (RecipeLine line : new ArrayList<>(lines)) {
            removeLine(line);
        }
    }

    public int totalMinutes() {
        return prepMinutes + cookMinutes;
    }

    public void recordCooked(Instant when) {
        this.timesCooked++;
        this.lastCookedAt = when;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public int getServings() {
        return servings;
    }

    public void setServings(int servings) {
        this.servings = servings;
    }

    public int getPrepMinutes() {
        return prepMinutes;
    }

    public void setPrepMinutes(int prepMinutes) {
        this.prepMinutes = prepMinutes;
    }

    public int getCookMinutes() {
        return cookMinutes;
    }

    public void setCookMinutes(int cookMinutes) {
        this.cookMinutes = cookMinutes;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public int getTimesCooked() {
        return timesCooked;
    }

    public Instant getLastCookedAt() {
        return lastCookedAt;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags == null ? new LinkedHashSet<>() : new LinkedHashSet<>(tags);
    }

    public List<RecipeLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
