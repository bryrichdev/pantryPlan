package edu.wgu.pantryprep.report;

import edu.wgu.pantryprep.domain.Recipe;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Shows which recipes have been cooked, ordered by frequency. */
public class RecipeUsageReport extends Report {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a");

    private final List<Recipe> recipes;

    public RecipeUsageReport(List<Recipe> recipes, Instant generatedAt) {
        super("Recipe usage report", generatedAt,
                List.of("Recipe", "Meal type", "Times cooked", "Last cooked"));
        this.recipes = List.copyOf(recipes);
    }

    @Override
    public void generate() {
        List<ReportRow> rows = new ArrayList<>();
        for (Recipe recipe : recipes) {
            rows.add(new ReportRow(List.of(
                    recipe.getName(),
                    recipe.getMealType() == null ? "Not set" : recipe.getMealType(),
                    Integer.toString(recipe.getTimesCooked()),
                    formatCookedAt(recipe.getLastCookedAt()))));
        }
        setRows(rows);
    }

    private String formatCookedAt(Instant cookedAt) {
        return cookedAt == null ? "Not cooked yet"
                : DATE_TIME.format(cookedAt.atZone(ZoneId.systemDefault()));
    }
}
