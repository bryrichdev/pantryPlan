package edu.wgu.pantryprep.importing;

import java.io.Serializable;
import java.util.List;

/**
 * What an upload would do (a preview) or did (an import). saved is true only
 * when an import committed.
 *
 * @param counts               rows read from each sheet that had any
 * @param recipes              names of the recipes it adds
 * @param pantryItems          how many pantry items it adds
 * @param stocked              the ingredients those pantry items are, each named once
 * @param newIngredients       ingredients it adds, from the Ingredients sheet or because a row named
 *                             one that isn't on the list yet
 * @param unchangedIngredients rows on the Ingredients sheet for ingredients already on the list,
 *                             which are left as they are
 */
public record ImportReport(boolean saved, List<ImportProblem> problems, List<SheetCount> counts,
                           List<String> recipes, int pantryItems, List<String> stocked,
                           List<String> newIngredients, List<String> unchangedIngredients) implements Serializable {

    public record SheetCount(String sheet, int rows) implements Serializable {
    }

    public static ImportReport failed(List<ImportProblem> problems) {
        return new ImportReport(false, problems, List.of(), List.of(), 0, List.of(), List.of(), List.of());
    }

    public boolean hasProblems() {
        return !problems.isEmpty();
    }

    /** "3 recipes, 12 pantry items and 9 new ingredients", for the message after importing. */
    public String summary() {
        List<String> parts = new java.util.ArrayList<>();
        if (!recipes.isEmpty()) {
            parts.add(recipes.size() + (recipes.size() == 1 ? " recipe" : " recipes"));
        }
        if (pantryItems > 0) {
            parts.add(pantryItems + (pantryItems == 1 ? " pantry item" : " pantry items"));
        }
        if (!newIngredients.isEmpty()) {
            parts.add(newIngredients.size() + (newIngredients.size() == 1 ? " new ingredient" : " new ingredients"));
        }
        if (parts.isEmpty()) {
            return "nothing new";
        }
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast();
    }
}
