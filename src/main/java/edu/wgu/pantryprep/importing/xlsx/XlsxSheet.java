package edu.wgu.pantryprep.importing.xlsx;

import java.util.List;

public record XlsxSheet(String name, List<XlsxRow> rows) {
}
