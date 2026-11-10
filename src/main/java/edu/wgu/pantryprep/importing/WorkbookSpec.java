package edu.wgu.pantryprep.importing;

import edu.wgu.pantryprep.importing.xlsx.XlsxWriter;
import edu.wgu.pantryprep.importing.xlsx.XlsxWriter.Cell;
import edu.wgu.pantryprep.importing.xlsx.XlsxWriter.Style;
import edu.wgu.pantryprep.importing.xlsx.XlsxWriter.TextRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * One downloadable workbook: its sheets, their columns, and what goes in each.
 * The template people download, its instructions sheet, and the upload parser
 * all read from here, so they can't drift apart.
 */
public record WorkbookSpec(String title, String fileName, List<String> howTo, List<Sheet> sheets) {

    public static final String INSTRUCTIONS = "Instructions";

    public record Column(String key, String header, ValueType type, boolean required, int width, String note) {

        /** Matches a header as typed, ignoring the " *" the template adds to required columns. */
        public boolean matchesHeader(String text) {
            return normalize(text).equals(normalize(header));
        }

        static String normalize(String text) {
            return text.replace("*", "").trim().replaceAll("\\s+", " ").toLowerCase();
        }
    }

    public record Sheet(String name, String purpose, List<Column> columns) {

        public Optional<Column> column(String key) {
            return columns.stream().filter(column -> column.key().equals(key)).findFirst();
        }

        /** The header a key is shown under, for messages. */
        public String headerFor(String key) {
            return column(key).map(Column::header).orElse(null);
        }
    }

    public static Column required(String key, String header, ValueType type, int width, String note) {
        return new Column(key, header, type, true, width, note);
    }

    public static Column optional(String key, String header, ValueType type, int width, String note) {
        return new Column(key, header, type, false, width, note);
    }

    public Optional<Sheet> sheet(String name) {
        return sheets.stream().filter(sheet -> sheet.name().equalsIgnoreCase(name.trim())).findFirst();
    }

    // ------------------------------------------------------- the download

    /** The template: the instructions first, then one empty sheet per kind of row. */
    public byte[] template() {
        List<XlsxWriter.Sheet> out = new ArrayList<>();
        out.add(instructions());
        for (Sheet sheet : sheets) {
            out.add(new XlsxWriter.TableSheet(sheet.name(), sheet.columns().stream()
                    .map(column -> new XlsxWriter.Column(column.header(), column.width(), column.required(),
                            column.type().format(), column.type().choices()))
                    .toList()));
        }
        return XlsxWriter.write(out);
    }

    private static final List<Integer> INSTRUCTION_WIDTHS = List.of(3, 22, 10, 84);

    private XlsxWriter.TextSheet instructions() {
        List<TextRow> rows = new ArrayList<>();
        rows.add(new TextRow(List.of(new Cell(title, Style.TITLE)), false, 24));
        rows.add(TextRow.of());
        rows.add(TextRow.of(new Cell("How it works", Style.HEADING)));
        for (String line : howTo) {
            rows.add(new TextRow(List.of(new Cell("•", Style.WRAP), Cell.of(line)), true, heightFor(line, 115)));
        }
        for (Sheet sheet : sheets) {
            rows.add(TextRow.of());
            rows.add(TextRow.of(new Cell(sheet.name(), Style.HEADING)));
            rows.add(new TextRow(List.of(new Cell("", Style.WRAP), new Cell(sheet.purpose(), Style.MUTED)),
                    true, heightFor(sheet.purpose(), 115)));
            rows.add(TextRow.of(new Cell("", Style.WRAP), new Cell("Column", Style.BOLD),
                    new Cell("Required", Style.BOLD), new Cell("What to enter", Style.BOLD)));
            for (Column column : sheet.columns()) {
                String what = Stream.of(column.type().describe(), column.note())
                        .filter(part -> part != null && !part.isBlank())
                        .collect(Collectors.joining(". "));
                rows.add(new TextRow(List.of(new Cell("", Style.WRAP), Cell.of(column.header()),
                        Cell.of(column.required() ? "Yes" : ""), Cell.of(what)), false, heightFor(what, 80)));
            }
        }
        return new XlsxWriter.TextSheet(INSTRUCTIONS, INSTRUCTION_WIDTHS, rows);
    }

    /** Rough row height for wrapped text: 15 points a line at about this many characters a line. */
    private static double heightFor(String text, int charsPerLine) {
        int lines = Math.max(1, (int) Math.ceil(text.length() / (double) charsPerLine));
        return lines == 1 ? 0 : lines * 15 + 2;
    }
}
