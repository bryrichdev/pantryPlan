package edu.wgu.pantryprep.importing.xlsx;

import java.util.Map;

/** A row that has at least one non-blank cell. number is the row number Excel shows (1-based). */
public record XlsxRow(int number, Map<Integer, XlsxCell> cells) {

    /** The cell in a zero-based column, or null when it's empty. */
    public XlsxCell cell(int column) {
        return cells.get(column);
    }
}
