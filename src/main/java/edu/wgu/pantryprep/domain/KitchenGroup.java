package edu.wgu.pantryprep.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A circle of cooks who share recipes: friends, immediate family, and so on.
 *
 * <p>Named KitchenGroup rather than Group because GROUP is a reserved word in
 * both SQL and JPQL. Each user owns at most five, which the database enforces
 * with a trigger on owner_id.
 */
@Entity
@Table(name = "kitchen_groups")
public class KitchenGroup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    protected KitchenGroup() {
    }

    public KitchenGroup(User owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
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
}
