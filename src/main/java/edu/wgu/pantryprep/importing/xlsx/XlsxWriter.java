package edu.wgu.pantryprep.importing.xlsx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a simple .xlsx workbook using only the JDK: table sheets with a styled header row,
 * column formats and dropdown lists, and text sheets for instructions. Just enough of the
 * format for a fill-in template; no formulas or charts.
 */
public final class XlsxWriter {

    /** Rows below the header that get a column's format and dropdown. */
    static final int FILL_ROWS = 2_000;

    /**
     * Excel refuses an inline dropdown list longer than 255 characters, so a longer one is
     * written to this hidden sheet and the dropdown points at its cells instead.
     */
    public static final String LISTS_SHEET = "Lists";
    static final int INLINE_LIST_LIMIT = 250;

    public enum Format {
        /** Whatever Excel decides. */
        GENERAL(0),
        /** Kept as typed, so a value like 007 keeps its leading zeros. */
        TEXT(3),
        /** Shown as 2025-01-31. */
        DATE(4);

        private final int style;

        Format(int style) {
            this.style = style;
        }
    }

    public enum Style {
        NORMAL(0), HEADER(1), REQUIRED_HEADER(2), TITLE(5), HEADING(6), WRAP(7), MUTED(8), BOLD(9);

        private final int index;

        Style(int index) {
            this.index = index;
        }
    }

    /** One column of a table sheet. A non-empty choices list becomes a dropdown. */
    public record Column(String header, int width, boolean required, Format format, List<String> choices) {
    }

    public record Cell(String text, Style style) {
        public static Cell of(String text) {
            return new Cell(text, Style.WRAP);
        }
    }

    public sealed interface Sheet permits TableSheet, TextSheet {
        String name();
    }

    /**
     * A header row, frozen, then rows of text (the template has none; tests and exports do).
     * A null or empty value leaves its cell empty.
     */
    public record TableSheet(String name, List<Column> columns, List<List<String>> rows) implements Sheet {
        public TableSheet(String name, List<Column> columns) {
            this(name, columns, List.of());
        }
    }

    /**
     * Free text. widths are in characters, one per column. A row's cells start in column A;
     * a row with span set runs its last cell across to the last column, and a row with a
     * height gets that height in points (Excel doesn't grow rows for wrapped merged cells).
     */
    public record TextSheet(String name, List<Integer> widths, List<TextRow> rows) implements Sheet {
    }

    public record TextRow(List<Cell> cells, boolean span, double height) {
        public static TextRow of(Cell... cells) {
            return new TextRow(List.of(cells), false, 0);
        }
    }

    private XlsxWriter() {
    }

    public static byte[] write(List<Sheet> sheets) {
        Map<List<String>, String> longLists = new LinkedHashMap<>();
        for (Sheet sheet : sheets) {
            if (sheet instanceof TableSheet table) {
                for (Column column : table.columns()) {
                    if (String.join(",", column.choices()).length() > INLINE_LIST_LIMIT) {
                        String letter = columnLetter(longLists.size());
                        longLists.computeIfAbsent(column.choices(), choices -> LISTS_SHEET + "!$" + letter + "$1:$"
                                + letter + "$" + choices.size());
                    }
                }
            }
        }
        List<String> names = new ArrayList<>(sheets.stream().map(Sheet::name).toList());
        if (!longLists.isEmpty()) {
            names.add(LISTS_SHEET);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", contentTypes(names.size()));
            put(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">\
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>\
                    </Relationships>""");
            put(zip, "xl/workbook.xml", workbook(names, !longLists.isEmpty()));
            put(zip, "xl/_rels/workbook.xml.rels", workbookRelationships(names.size()));
            put(zip, "xl/styles.xml", STYLES);
            for (int i = 0; i < sheets.size(); i++) {
                String xml = switch (sheets.get(i)) {
                    case TableSheet table -> tableSheet(table, i == 0, longLists);
                    case TextSheet text -> textSheet(text, i == 0);
                };
                put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", xml);
            }
            if (!longLists.isEmpty()) {
                put(zip, "xl/worksheets/sheet" + names.size() + ".xml", listsSheet(longLists.keySet()));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return bytes.toByteArray();
    }

    // ============ package parts ============

    private static String contentTypes(int sheetCount) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">\
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>\
                <Default Extension="xml" ContentType="application/xml"/>\
                <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>\
                <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""");
        for (int i = 1; i <= sheetCount; i++) {
            xml.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return xml.append("</Types>").toString();
    }

    private static String workbook(List<String> names, boolean lastHidden) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" \
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">\
                <bookViews><workbookView activeTab="0"/></bookViews><sheets>""");
        for (int i = 0; i < names.size(); i++) {
            xml.append("<sheet name=\"").append(escape(names.get(i))).append("\" sheetId=\"").append(i + 1)
                    .append(lastHidden && i == names.size() - 1 ? "\" state=\"hidden" : "")
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return xml.append("</sheets></workbook>").toString();
    }

    private static String workbookRelationships(int sheetCount) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""");
        for (int i = 1; i <= sheetCount; i++) {
            xml.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i).append(".xml\"/>");
        }
        xml.append("<Relationship Id=\"rId").append(sheetCount + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return xml.append("</Relationships>").toString();
    }

    /**
     * cellXfs indexes: 0 plain, 1 header, 2 required header, 3 text, 4 date, 5 title,
     * 6 heading, 7 wrapped, 8 muted, 9 bold. Style and Format refer to these.
     */
    private static final String STYLES = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">\
            <numFmts count="1"><numFmt numFmtId="164" formatCode="yyyy\\-mm\\-dd"/></numFmts>\
            <fonts count="5">\
            <font><sz val="11"/><name val="Calibri"/><family val="2"/></font>\
            <font><b/><sz val="11"/><name val="Calibri"/><family val="2"/></font>\
            <font><b/><sz val="16"/><color rgb="FF2F5D3F"/><name val="Calibri"/><family val="2"/></font>\
            <font><sz val="10"/><color rgb="FF6A6155"/><name val="Calibri"/><family val="2"/></font>\
            <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/><family val="2"/></font>\
            </fonts>\
            <fills count="4">\
            <fill><patternFill patternType="none"/></fill>\
            <fill><patternFill patternType="gray125"/></fill>\
            <fill><patternFill patternType="solid"><fgColor rgb="FFEFE8DA"/><bgColor indexed="64"/></patternFill></fill>\
            <fill><patternFill patternType="solid"><fgColor rgb="FF2F5D3F"/><bgColor indexed="64"/></patternFill></fill>\
            </fills>\
            <borders count="2">\
            <border><left/><right/><top/><bottom/><diagonal/></border>\
            <border><left/><right/><top/><bottom style="thin"><color rgb="FFD2C6B0"/></bottom><diagonal/></border>\
            </borders>\
            <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>\
            <cellXfs count="10">\
            <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>\
            <xf numFmtId="49" fontId="1" fillId="2" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>\
            <xf numFmtId="49" fontId="4" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>\
            <xf numFmtId="49" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>\
            <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>\
            <xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>\
            <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment vertical="bottom"/></xf>\
            <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>\
            <xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>\
            <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>\
            </cellXfs>\
            <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>\
            </styleSheet>""";

    // ============ sheets ============

    private static String tableSheet(TableSheet sheet, boolean selected, Map<List<String>, String> longLists) {
        List<Column> columns = sheet.columns();
        StringBuilder xml = sheetStart();
        xml.append("<sheetViews><sheetView workbookViewId=\"0\"").append(selected ? " tabSelected=\"1\"" : "")
                .append("><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
                .append("<selection pane=\"bottomLeft\" activeCell=\"A2\" sqref=\"A2\"/></sheetView></sheetViews>")
                .append("<sheetFormatPr defaultRowHeight=\"15\"/><cols>");
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            xml.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                    .append("\" width=\"").append(column.width()).append("\" customWidth=\"1\"");
            if (column.format().style != 0) {
                xml.append(" style=\"").append(column.format().style).append("\"");
            }
            xml.append("/>");
        }
        xml.append("</cols><sheetData><row r=\"1\" ht=\"32\" customHeight=\"1\">");
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            String header = column.required() ? column.header() + " *" : column.header();
            Style style = column.required() ? Style.REQUIRED_HEADER : Style.HEADER;
            inlineCell(xml, i, 1, header, style.index);
        }
        xml.append("</row>");
        for (int r = 0; r < sheet.rows().size(); r++) {
            List<String> values = sheet.rows().get(r);
            xml.append("<row r=\"").append(r + 2).append("\">");
            for (int c = 0; c < values.size(); c++) {
                String value = values.get(c);
                if (value != null && !value.isEmpty()) {
                    inlineCell(xml, c, r + 2, value, 0);
                }
            }
            xml.append("</row>");
        }
        xml.append("</sheetData>");

        long withChoices = columns.stream().filter(column -> !column.choices().isEmpty()).count();
        if (withChoices > 0) {
            xml.append("<dataValidations count=\"").append(withChoices).append("\">");
            for (int i = 0; i < columns.size(); i++) {
                Column column = columns.get(i);
                if (column.choices().isEmpty()) {
                    continue;
                }
                String letter = columnLetter(i);
                xml.append("<dataValidation type=\"list\" allowBlank=\"1\" showErrorMessage=\"1\"")
                        .append(" errorTitle=\"Pick from the list\" error=\"Choose one of the values in the dropdown.\"")
                        .append(" sqref=\"").append(letter).append("2:").append(letter).append(FILL_ROWS + 1).append("\">")
                        .append("<formula1>").append(longLists.containsKey(column.choices())
                                ? escape(longLists.get(column.choices()))
                                : "&quot;" + escape(String.join(",", column.choices())) + "&quot;")
                        .append("</formula1></dataValidation>");
            }
            xml.append("</dataValidations>");
        }
        return sheetEnd(xml);
    }

    /** The hidden sheet behind long dropdowns: one list per column, from row 1. */
    private static String listsSheet(Iterable<List<String>> lists) {
        List<List<String>> columns = new ArrayList<>();
        lists.forEach(columns::add);
        int longest = columns.stream().mapToInt(List::size).max().orElse(0);
        StringBuilder xml = sheetStart().append("<sheetData>");
        for (int r = 0; r < longest; r++) {
            xml.append("<row r=\"").append(r + 1).append("\">");
            for (int c = 0; c < columns.size(); c++) {
                if (r < columns.get(c).size()) {
                    inlineCell(xml, c, r + 1, columns.get(c).get(r), 0);
                }
            }
            xml.append("</row>");
        }
        return sheetEnd(xml.append("</sheetData>"));
    }

    private static String textSheet(TextSheet sheet, boolean selected) {
        StringBuilder xml = sheetStart();
        // Printed one page wide, however many pages long.
        xml.append("<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr>");
        xml.append("<sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"0\"")
                .append(selected ? " tabSelected=\"1\"" : "").append("/></sheetViews>")
                .append("<sheetFormatPr defaultRowHeight=\"15\"/><cols>");
        for (int i = 0; i < sheet.widths().size(); i++) {
            xml.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                    .append("\" width=\"").append(sheet.widths().get(i)).append("\" customWidth=\"1\"/>");
        }
        xml.append("</cols><sheetData>");
        List<String> merges = new java.util.ArrayList<>();
        String lastColumn = columnLetter(sheet.widths().size() - 1);
        for (int r = 0; r < sheet.rows().size(); r++) {
            TextRow row = sheet.rows().get(r);
            List<Cell> cells = row.cells();
            xml.append("<row r=\"").append(r + 1).append("\"");
            if (row.height() > 0) {
                xml.append(" ht=\"").append(row.height()).append("\" customHeight=\"1\"");
            }
            xml.append(">");
            for (int c = 0; c < cells.size(); c++) {
                Cell cell = cells.get(c);
                if (cell != null && cell.text() != null && !cell.text().isEmpty()) {
                    inlineCell(xml, c, r + 1, cell.text(), cell.style().index);
                }
            }
            xml.append("</row>");
            if (row.span() && !cells.isEmpty() && cells.size() < sheet.widths().size()) {
                merges.add(columnLetter(cells.size() - 1) + (r + 1) + ":" + lastColumn + (r + 1));
            }
        }
        xml.append("</sheetData>");
        if (!merges.isEmpty()) {
            xml.append("<mergeCells count=\"").append(merges.size()).append("\">");
            merges.forEach(range -> xml.append("<mergeCell ref=\"").append(range).append("\"/>"));
            xml.append("</mergeCells>");
        }
        return sheetEnd(xml, "<pageSetup orientation=\"portrait\" fitToWidth=\"1\" fitToHeight=\"0\"/>");
    }

    private static StringBuilder sheetStart() {
        return new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" \
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""");
    }

    private static String sheetEnd(StringBuilder xml) {
        return sheetEnd(xml, "");
    }

    private static String sheetEnd(StringBuilder xml, String pageSetup) {
        return xml.append("<pageMargins left=\"0.5\" right=\"0.5\" top=\"0.6\" bottom=\"0.6\" header=\"0.3\" footer=\"0.3\"/>")
                .append(pageSetup).append("</worksheet>").toString();
    }

    private static void inlineCell(StringBuilder xml, int column, int row, String text, int style) {
        xml.append("<c r=\"").append(columnLetter(column)).append(row).append("\" t=\"inlineStr\"");
        if (style != 0) {
            xml.append(" s=\"").append(style).append("\"");
        }
        xml.append("><is><t xml:space=\"preserve\">").append(escape(text)).append("</t></is></c>");
    }

    // ============ helpers ============

    /** 0 is A, 25 is Z, 26 is AA. */
    public static String columnLetter(int index) {
        StringBuilder letters = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            int remainder = (n - 1) % 26;
            letters.insert(0, (char) ('A' + remainder));
            n = (n - 1) / 26;
        }
        return letters.toString();
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) {
            switch (ch) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> {
                    // XML 1.0 can't carry most control characters at all.
                    if (ch >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') {
                        out.append(ch);
                    }
                }
            }
        }
        return out.toString();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
