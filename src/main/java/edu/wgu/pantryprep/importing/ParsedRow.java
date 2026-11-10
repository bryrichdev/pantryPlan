package edu.wgu.pantryprep.importing;

import edu.wgu.pantryprep.importing.WorkbookSpec.Sheet;
import java.util.Map;

/**
 * One data row, its cells already converted. number is the row number Excel
 * shows, so messages can point straight at it. A blank cell has no entry.
 */
public record ParsedRow(Sheet sheet, int number, Map<String, Object> values) {

    public boolean has(String key) {
        return values.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) values.get(key);
    }

    public String text(String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }
}
