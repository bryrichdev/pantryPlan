package edu.wgu.pantryprep.web.form;

import edu.wgu.pantryprep.domain.StorageLocation;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** One bought line in the stock-up dialog. */
public class StockUpRow {

    private Long itemId;

    /** Cleared for anything on the list that was not actually bought. */
    private boolean include = true;

    /**
     * How much was bought, in the ingredient's stocking unit. Not the amount
     * the list asked for: you buy a five pound bag to cover half a pound.
     */
    private BigDecimal quantity;

    private StorageLocation location = StorageLocation.PANTRY;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expiresOn;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public boolean isInclude() {
        return include;
    }

    public void setInclude(boolean include) {
        this.include = include;
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

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public void setExpiresOn(LocalDate expiresOn) {
        this.expiresOn = expiresOn;
    }
}
