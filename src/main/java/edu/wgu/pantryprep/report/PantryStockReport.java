package edu.wgu.pantryprep.report;

import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.PantryItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Totals every current pantry row for each ingredient. */
public class PantryStockReport extends Report {

    private final List<PantryItem> pantryItems;

    public PantryStockReport(List<PantryItem> pantryItems, Instant generatedAt) {
        super("Pantry stock report", generatedAt, List.of("Ingredient", "Category", "On hand"));
        this.pantryItems = List.copyOf(pantryItems);
    }

    @Override
    public void generate() {
        Map<Long, StockTotal> totals = new LinkedHashMap<>();
        for (PantryItem item : pantryItems) {
            Ingredient ingredient = item.getIngredient();
            StockTotal existing = totals.get(ingredient.getId());
            if (existing == null) {
                totals.put(ingredient.getId(), new StockTotal(ingredient, item.getQuantity()));
            } else {
                totals.put(ingredient.getId(), new StockTotal(ingredient,
                        existing.quantity().add(item.getQuantity())));
            }
        }

        List<ReportRow> rows = new ArrayList<>();
        for (StockTotal total : totals.values()) {
            Ingredient ingredient = total.ingredient();
            rows.add(new ReportRow(List.of(
                    ingredient.getName(),
                    ReportText.label(ingredient.getCategory()),
                    ReportText.pantryQuantity(total.quantity()) + " " + ingredient.getStockUnit().getAbbreviation())));
        }
        setRows(rows);
    }

    private record StockTotal(Ingredient ingredient, BigDecimal quantity) {
    }
}
