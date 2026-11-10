package edu.wgu.pantryprep.importing;

import edu.wgu.pantryprep.importing.WorkbookSpec.Column;
import edu.wgu.pantryprep.importing.WorkbookSpec.Sheet;
import edu.wgu.pantryprep.importing.xlsx.XlsxCell;
import edu.wgu.pantryprep.importing.xlsx.XlsxRow;
import edu.wgu.pantryprep.importing.xlsx.XlsxSheet;
import edu.wgu.pantryprep.importing.xlsx.XlsxWorkbook;
import edu.wgu.pantryprep.importing.xlsx.XlsxWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns an uploaded workbook into converted rows, sheet by sheet. Columns are
 * found by their header, so reordered columns still read correctly; renamed or
 * extra ones are reported rather than quietly skipped, since skipping them
 * would drop data without anyone noticing.
 */
public final class SheetParser {

    private SheetParser() {
    }

    /** Rows for every sheet of the spec, in spec order; a sheet missing from the file has none. */
    public static Map<Sheet, List<ParsedRow>> parse(WorkbookSpec spec, XlsxWorkbook workbook, Problems problems) {
        Map<Sheet, List<ParsedRow>> rows = new LinkedHashMap<>();
        spec.sheets().forEach(sheet -> rows.put(sheet, new ArrayList<>()));

        Set<Sheet> seen = new HashSet<>();
        for (XlsxSheet xlsxSheet : workbook.sheets()) {
            String name = xlsxSheet.name().trim();
            if (name.equalsIgnoreCase(WorkbookSpec.INSTRUCTIONS) || name.equalsIgnoreCase(XlsxWriter.LISTS_SHEET)) {
                continue;
            }
            Sheet sheet = spec.sheet(name).orElse(null);
            if (sheet == null) {
                if (!xlsxSheet.rows().isEmpty()) {
                    problems.sheet(name, "This sheet isn't part of the template, so nothing on it can be read. "
                            + "Move its rows to the matching template sheet, or delete it.");
                }
                continue;
            }
            if (!seen.add(sheet)) {
                problems.sheet(name, "The file has two sheets named " + sheet.name() + ". Merge them into one.");
                continue;
            }
            rows.get(sheet).addAll(parseSheet(sheet, xlsxSheet, problems));
        }
        return rows;
    }

    private static List<ParsedRow> parseSheet(Sheet sheet, XlsxSheet xlsxSheet, Problems problems) {
        List<XlsxRow> xlsxRows = xlsxSheet.rows();
        if (xlsxRows.isEmpty()) {
            return List.of();
        }
        XlsxRow header = xlsxRows.getFirst();
        Map<Integer, Column> columns = readHeader(sheet, header, problems);
        List<XlsxRow> dataRows = xlsxRows.subList(1, xlsxRows.size());
        if (dataRows.isEmpty()) {
            return List.of();
        }

        for (Column column : sheet.columns()) {
            if (column.required() && !columns.containsValue(column)) {
                problems.sheet(sheet.name(), "The required column \"" + column.header() + "\" is missing. "
                        + "Put its header back in row " + header.number() + ".");
            }
        }

        List<ParsedRow> parsed = new ArrayList<>();
        for (XlsxRow row : dataRows) {
            Map<String, Object> values = new HashMap<>();
            for (Map.Entry<Integer, Column> entry : columns.entrySet()) {
                Column column = entry.getValue();
                XlsxCell cell = row.cell(entry.getKey());
                if (cell == null || cell.isBlank()) {
                    continue;
                }
                try {
                    Object value = column.type().convert(cell);
                    if (value instanceof String text && text.isEmpty()) {
                        continue;
                    }
                    values.put(column.key(), value);
                } catch (ValueType.BadValue ex) {
                    problems.cell(sheet.name(), row.number(), column.header(), ex.getMessage());
                }
            }
            // A row whose only content sits in columns without a header isn't data
            // anyone can have meant to import.
            if (values.isEmpty() && !hasRecognisedCell(row, columns)) {
                continue;
            }
            for (Column column : sheet.columns()) {
                // A cell that failed to convert already has its own message, and the first wins.
                if (column.required() && columns.containsValue(column) && !values.containsKey(column.key())) {
                    problems.cell(sheet.name(), row.number(), column.header(), column.header() + " is required");
                }
            }
            parsed.add(new ParsedRow(sheet, row.number(), values));
        }
        return parsed;
    }

    private static Map<Integer, Column> readHeader(Sheet sheet, XlsxRow header, Problems problems) {
        Map<Integer, Column> columns = new LinkedHashMap<>();
        for (Map.Entry<Integer, XlsxCell> entry : header.cells().entrySet()) {
            String text = entry.getValue().text();
            if (text == null || text.isBlank()) {
                continue;
            }
            Column column = sheet.columns().stream()
                    .filter(candidate -> candidate.matchesHeader(text))
                    .findFirst()
                    .orElse(null);
            if (column == null) {
                problems.sheet(sheet.name(), "The column \"" + text.trim() + "\" isn't in the template. "
                        + "Rename it to a template column or delete it.");
            } else if (columns.containsValue(column)) {
                problems.sheet(sheet.name(), "The column \"" + column.header() + "\" appears twice.");
            } else {
                columns.put(entry.getKey(), column);
            }
        }
        return columns;
    }

    private static boolean hasRecognisedCell(XlsxRow row, Map<Integer, Column> columns) {
        return columns.keySet().stream().anyMatch(index -> {
            XlsxCell cell = row.cell(index);
            return cell != null && !cell.isBlank();
        });
    }
}
