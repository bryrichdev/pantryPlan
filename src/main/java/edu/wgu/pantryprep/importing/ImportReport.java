package edu.wgu.pantryprep.importing;

import java.io.Serializable;
import java.util.List;

/**
 * What an upload would do (a preview) or did (an import). saved is true only
 * when an import committed. created names what the import adds, such as the
 * recipes; newIngredients names the ingredients it adds because no row matched
 * one already on the list.
 */
public record ImportReport(boolean saved, List<ImportProblem> problems, List<SheetCount> counts,
                           List<String> created, List<String> newIngredients) implements Serializable {

    public record SheetCount(String sheet, int rows) implements Serializable {
    }

    public static ImportReport failed(List<ImportProblem> problems) {
        return new ImportReport(false, problems, List.of(), List.of(), List.of());
    }

    public boolean hasProblems() {
        return !problems.isEmpty();
    }
}
