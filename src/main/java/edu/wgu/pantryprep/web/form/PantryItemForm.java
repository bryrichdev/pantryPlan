package edu.wgu.pantryprep.web.form;

import edu.wgu.pantryprep.domain.PantryItem;
import edu.wgu.pantryprep.domain.StorageLocation;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Backing object for the pantry item dialog.
 *
 * <p>No unit field: the amount is always in the ingredient's stocking unit.
 */
public class PantryItemForm {

    private Long id;

    @NotNull(message = "Choose an ingredient")
    private Long ingredientId;

    @NotNull(message = "Enter how much you have")
    @DecimalMin(value = "0.000", message = "Amount cannot be negative")
    @Digits(integer = 7, fraction = 1, message = "Use up to one decimal place")
    private BigDecimal quantity;

    @NotNull(message = "Choose where it is stored")
    private StorageLocation location = StorageLocation.PANTRY;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate purchasedOn = LocalDate.now();

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expiresOn;

    public static PantryItemForm from(PantryItem item) {
        PantryItemForm form = new PantryItemForm();
        form.setId(item.getId());
        form.setIngredientId(item.getIngredient().getId());
        form.setQuantity(item.getQuantity());
        form.setLocation(item.getLocation());
        form.setPurchasedOn(item.getPurchasedOn() != null ? item.getPurchasedOn() : LocalDate.now());
        form.setExpiresOn(item.getExpiresOn());
        return form;
    }

    public boolean isNew() {
        return id == null;
    }

    /**
     * True when both dates are present and the expiry falls before the purchase,
     * which is almost always a typo rather than an intention.
     */
    public boolean hasBackwardsDates() {
        return purchasedOn != null && expiresOn != null && expiresOn.isBefore(purchasedOn);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIngredientId() {
        return ingredientId;
    }

    public void setIngredientId(Long ingredientId) {
        this.ingredientId = ingredientId;
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
