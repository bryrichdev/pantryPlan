package edu.wgu.pantryplan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "ingredients")
public class Ingredient extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private IngredientCategory category = IngredientCategory.OTHER;

    @Column(name = "grams_per_cup", precision = 10, scale = 3)
    private BigDecimal gramsPerCup;

    protected Ingredient() {
    }

    public Ingredient(User user, String name, IngredientCategory category) {
        this.user = user;
        this.name = name;
        this.category = category;
    }

    public boolean hasVolumeWeightRatio() {
        return gramsPerCup != null && gramsPerCup.signum() > 0;
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

    public IngredientCategory getCategory() {
        return category;
    }

    public void setCategory(IngredientCategory category) {
        this.category = category;
    }

    public BigDecimal getGramsPerCup() {
        return gramsPerCup;
    }

    public void setGramsPerCup(BigDecimal gramsPerCup) {
        this.gramsPerCup = gramsPerCup;
    }
}