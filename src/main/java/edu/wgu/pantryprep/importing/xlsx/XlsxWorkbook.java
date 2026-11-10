package edu.wgu.pantryprep.importing.xlsx;

import java.util.List;
import java.util.Optional;

public record XlsxWorkbook(List<XlsxSheet> sheets) {

    /** Sheet names are matched the way Excel treats them: ignoring case. */
    public Optional<XlsxSheet> sheet(String name) {
        return sheets.stream().filter(sheet -> sheet.name().trim().equalsIgnoreCase(name)).findFirst();
    }
}
