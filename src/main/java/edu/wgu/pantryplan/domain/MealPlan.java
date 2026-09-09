package edu.wgu.pantryplan.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "meal_plans")
public class MealPlan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @OneToMany(mappedBy = "mealPlan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PlanEntry> entries = new ArrayList<>();

    protected MealPlan() {
    }

    public MealPlan(User user, String name, LocalDate weekStartDate) {
        this.user = user;
        this.name = name;
        this.weekStartDate = weekStartDate;
    }

    public void addEntry(PlanEntry entry) {
        entries.add(entry);
        entry.setMealPlan(this);
    }

    public void removeEntry(PlanEntry entry) {
        entries.remove(entry);
        entry.setMealPlan(null);
    }

    public LocalDate weekEndDate() {
        return weekStartDate.plusDays(6);
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

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public void setWeekStartDate(LocalDate weekStartDate) {
        this.weekStartDate = weekStartDate;
    }

    public List<PlanEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }
}
