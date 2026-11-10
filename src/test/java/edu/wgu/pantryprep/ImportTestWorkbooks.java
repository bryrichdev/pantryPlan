package edu.wgu.pantryprep;

import edu.wgu.pantryprep.importing.WorkbookSpec;
import edu.wgu.pantryprep.importing.WorkbookSpec.Column;
import edu.wgu.pantryprep.importing.WorkbookSpec.Sheet;
import edu.wgu.pantryprep.importing.xlsx.XlsxWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds filled-in import spreadsheets for tests the way a cook would: the
 * template's headers in row 1 and text typed in below.
 */
final class ImportTestWorkbooks {

    private final Map<Sheet, List<List<String>>> rows = new LinkedHashMap<>();
    private final WorkbookSpec spec;

    private ImportTestWorkbooks(WorkbookSpec spec) {
        this.spec = spec;
        spec.sheets().forEach(sheet -> rows.put(sheet, new ArrayList<>()));
    }

    static ImportTestWorkbooks of(WorkbookSpec spec) {
        return new ImportTestWorkbooks(spec);
    }

    /** One row, values in the sheet's column order; null or "" leaves a cell blank. */
    ImportTestWorkbooks row(Sheet sheet, String... values) {
        rows.get(sheet).add(Arrays.asList(values));
        return this;
    }

    byte[] bytes() {
        List<XlsxWriter.Sheet> sheets = new ArrayList<>();
        rows.forEach((sheet, data) -> {
            List<XlsxWriter.Column> columns = new ArrayList<>();
            for (Column column : sheet.columns()) {
                columns.add(new XlsxWriter.Column(column.header(), column.width(), column.required(),
                        XlsxWriter.Format.GENERAL, List.of()));
            }
            sheets.add(new XlsxWriter.TableSheet(sheet.name(), columns, data));
        });
        return XlsxWriter.write(sheets);
    }
}
