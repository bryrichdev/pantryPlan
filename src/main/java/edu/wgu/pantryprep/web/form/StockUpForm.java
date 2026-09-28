package edu.wgu.pantryprep.web.form;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Backing object for putting a shop away: one purchase date for the trip, and
 * a row for each bought line.
 *
 * <p>The date is shared because a grocery list is one shop. Anything bought on
 * a different day is easier to fix on the pantry page than to enter eleven
 * times here.
 */
public class StockUpForm {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate purchasedOn = LocalDate.now();

    /**
     * Indexed binding, so a rejected form comes back with every row as it was
     * typed. Spring grows the list as it binds, hence ArrayList rather than
     * List.of.
     */
    private List<StockUpRow> rows = new ArrayList<>();

    public LocalDate getPurchasedOn() {
        return purchasedOn;
    }

    public void setPurchasedOn(LocalDate purchasedOn) {
        this.purchasedOn = purchasedOn;
    }

    public List<StockUpRow> getRows() {
        return rows;
    }

    public void setRows(List<StockUpRow> rows) {
        this.rows = rows;
    }

    /** Rows the cook actually ticked to put away. */
    public List<StockUpRow> included() {
        return rows.stream().filter(StockUpRow::isInclude).toList();
    }
}
