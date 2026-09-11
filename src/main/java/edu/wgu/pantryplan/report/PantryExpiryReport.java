package edu.wgu.pantryplan.report;

import edu.wgu.pantryplan.domain.PantryItem;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Lists individual pantry rows that are either expiring soon or already expired. */
public class PantryExpiryReport extends Report {

    public enum Scope {
        NEAR_EXPIRY,
        EXPIRED
    }

    private final List<PantryItem> pantryItems;
    private final LocalDate today;
    private final int warningDays;
    private final Scope scope;

    public PantryExpiryReport(List<PantryItem> pantryItems, LocalDate today,
                              int warningDays, Scope scope, Instant generatedAt) {
        super(titleFor(scope), generatedAt,
                List.of("Ingredient", "On hand", "Stored in", "Expires", "Status"));
        this.pantryItems = List.copyOf(pantryItems);
        this.today = today;
        this.warningDays = warningDays;
        this.scope = scope;
    }

    @Override
    public void generate() {
        List<PantryItem> matching = pantryItems.stream()
                .filter(this::matchesScope)
                .sorted(Comparator.comparing(PantryItem::getExpiresOn))
                .toList();

        List<ReportRow> rows = new ArrayList<>();
        for (PantryItem item : matching) {
            rows.add(new ReportRow(List.of(
                    item.getIngredient().getName(),
                    ReportText.quantity(item.getQuantity()) + " " + item.getUnit().getAbbreviation(),
                    ReportText.label(item.getLocation()),
                    ReportText.date(item.getExpiresOn()),
                    expiryStatus(item.getExpiresOn()))));
        }
        setRows(rows);
    }

    private boolean matchesScope(PantryItem item) {
        if (item.getExpiresOn() == null) {
            return false;
        }
        if (scope == Scope.EXPIRED) {
            return item.getExpiresOn().isBefore(today);
        }
        return !item.getExpiresOn().isBefore(today)
                && !item.getExpiresOn().isAfter(today.plusDays(warningDays));
    }

    private String expiryStatus(LocalDate expiresOn) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, expiresOn);
        if (days == 0) {
            return "Expires today";
        }
        if (days == 1) {
            return "1 day left";
        }
        if (days > 1) {
            return days + " days left";
        }
        long overdue = Math.abs(days);
        return overdue == 1 ? "1 day overdue" : overdue + " days overdue";
    }

    private static String titleFor(Scope scope) {
        return scope == Scope.NEAR_EXPIRY
                ? "Near-expired pantry items"
                : "Expired pantry items";
    }
}
