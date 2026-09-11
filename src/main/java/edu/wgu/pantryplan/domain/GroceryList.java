package edu.wgu.pantryplan.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "grocery_lists")
public class GroceryList extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_plan_id")
    private MealPlan mealPlan;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @OneToMany(mappedBy = "groceryList", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<GroceryListItem> items = new ArrayList<>();

    protected GroceryList() {
    }

    public GroceryList(User user, MealPlan mealPlan, String title, Instant generatedAt) {
        this.user = user;
        this.mealPlan = mealPlan;
        this.title = title;
        this.generatedAt = generatedAt;
    }

    public void addItem(GroceryListItem item) {
        items.add(item);
        item.setGroceryList(this);
    }

    public int itemCount() {
        return items.size();
    }

    public long remainingCount() {
        return items.stream().filter(i -> !i.isPurchased()).count();
    }

    public long purchasedCount() {
        return items.stream().filter(GroceryListItem::isPurchased).count();
    }

    public long needsReviewCount() {
        return items.stream().filter(GroceryListItem::isNeedsReview).count();
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public MealPlan getMealPlan() {
        return mealPlan;
    }

    public void setMealPlan(MealPlan mealPlan) {
        this.mealPlan = mealPlan;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public List<GroceryListItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
