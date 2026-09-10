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
import java.time.LocalDate;

@Entity
@Table(name = "pantry_items")
public class PantryItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(name = "quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "location", nullable = false, length = 30)
    private StorageLocation location = StorageLocation.PANTRY;

    @Column(name = "purchased_on")
    private LocalDate purchasedOn;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    protected PantryItem() {
    }

    public PantryItem(User user, Ingredient ingredient, BigDecimal quantity) {
        this.user = user;
        this.ingredient = ingredient;
        this.quantity = quantity;
        this.location = ingredient.getDefaultLocation();
    }

    /**
     * Derived from the ingredient rather than stored per row. Hibernate maps
     * fields, not getters, so this is invisible to persistence.
     */
    public Unit getUnit() {
        return ingredient.getStockUnit();
    }

    public boolean isExpiringWithin(int days, LocalDate today) {
        if (expiresOn == null) {
            return false;
        }
        return !expiresOn.isAfter(today.plusDays(days));
    }

    public boolean isExpired(LocalDate today) {
        return expiresOn != null && expiresOn.isBefore(today);
    }

    public void deduct(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Deduction amount must not be negative");
        }
        BigDecimal remaining = quantity.subtract(amount);
        this.quantity = remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
    }

    public void restore(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Restore amount must not be negative");
        }
        this.quantity = quantity.add(amount);
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public void setIngredient(Ingredient ingredient) {
        this.ingredient = ingredient;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public StorageLocation getLocation() {
        return location;
    }

    public void setLocation(StorageLocation location) {
        this.location = location;
    }

    public LocalDate getPurchasedOn() {
        return purchasedOn;
    }

    public void setPurchasedOn(LocalDate purchasedOn) {
        this.purchasedOn = purchasedOn;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public void setExpiresOn(LocalDate expiresOn) {
        this.expiresOn = expiresOn;
    }
}
