package edu.wgu.pantryprep.importing;

import java.io.Serializable;

/**
 * Something that stops an import, pinned as closely as possible: the whole file
 * (sheet null), a sheet (row null), a row (column null) or one cell.
 */
public record ImportProblem(String sheet, Integer row, String column, String message) implements Serializable {
}
