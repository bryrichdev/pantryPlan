package edu.wgu.pantryprep.importing.xlsx;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Reads the cell values out of an .xlsx file, using only the JDK.
 *
 * An .xlsx file is a zip of XML parts. This reads the workbook's sheet list, the shared
 * strings, enough of the styles to tell dates and percentages from plain numbers, and each
 * sheet's cells. Formulas give their last calculated value. Formatting, comments and
 * everything else are ignored.
 *
 * Uploads are untrusted, so the zip is capped in entries and in uncompressed size (a small
 * file can inflate to gigabytes), and the XML parser refuses DTDs and external entities.
 */
public final class XlsxReader {

    static final int MAX_ENTRIES = 2_000;
    static final long MAX_ENTRY_BYTES = 40L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 120L * 1024 * 1024;
    static final int MAX_ROWS_PER_SHEET = 20_000;
    static final int MAX_COLUMNS = 200;

    private static final String MAIN_NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final Pattern CELL_REF = Pattern.compile("^([A-Z]{1,3})([0-9]+)$");

    private XlsxReader() {
    }

    public static XlsxWorkbook read(InputStream in) throws XlsxException {
        Map<String, byte[]> parts = unzip(in);
        byte[] workbookXml = parts.get("xl/workbook.xml");
        if (workbookXml == null) {
            throw new XlsxException("This isn't an Excel workbook (.xlsx). Save it as an .xlsx file and try again.");
        }
        Workbook workbook = parseWorkbook(workbookXml);
        List<Relationship> relationships = parseRelationships(parts.get("xl/_rels/workbook.xml.rels"));
        Map<String, String> targets = new HashMap<>();
        relationships.forEach(relationship -> targets.put(relationship.id(), relationship.target()));
        List<String> strings = parseSharedStrings(
                parts.get(findPart(parts, relationships, "sharedStrings", "xl/sharedStrings.xml")));
        Styles styles = parseStyles(parts.get(findPart(parts, relationships, "styles", "xl/styles.xml")));

        List<XlsxSheet> sheets = new ArrayList<>();
        for (SheetRef ref : workbook.sheets()) {
            String target = targets.get(ref.relationId());
            if (target == null) {
                continue;
            }
            byte[] xml = parts.get(target);
            if (xml == null) {
                // Chart sheets and dialog sheets point at other kinds of part; skip anything
                // that isn't there rather than fail the whole file.
                continue;
            }
            sheets.add(new XlsxSheet(ref.name(), parseSheet(ref.name(), xml, strings, styles, workbook.date1904())));
        }
        return new XlsxWorkbook(sheets);
    }

    // ============ zip ============

    private static Map<String, byte[]> unzip(InputStream in) throws XlsxException {
        Map<String, byte[]> parts = new HashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            byte[] buffer = new byte[16 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                if (parts.size() >= MAX_ENTRIES) {
                    throw new XlsxException("The file has too many parts to be a spreadsheet.");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                long size = 0;
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    size += read;
                    total += read;
                    if (size > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) {
                        throw new XlsxException("The file is too large once unpacked. Split it into smaller files.");
                    }
                    out.write(buffer, 0, read);
                }
                parts.put(stripLeadingSlash(entry.getName()), out.toByteArray());
            }
        } catch (ZipException ex) {
            throw new XlsxException("This isn't an Excel workbook (.xlsx). Save it as an .xlsx file and try again.");
        } catch (IOException ex) {
            throw new XlsxException("The file couldn't be read. Save it again as .xlsx and retry.");
        }
        if (parts.isEmpty()) {
            throw new XlsxException("This isn't an Excel workbook (.xlsx). Save it as an .xlsx file and try again.");
        }
        return parts;
    }

    // ============ workbook and relationships ============

    private record SheetRef(String name, String relationId) {
    }

    private record Workbook(List<SheetRef> sheets, boolean date1904) {
    }

    private static Workbook parseWorkbook(byte[] xml) throws XlsxException {
        List<SheetRef> sheets = new ArrayList<>();
        boolean date1904 = false;
        XMLStreamReader reader = open(xml);
        try {
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                switch (reader.getLocalName()) {
                    case "workbookPr" -> {
                        String value = reader.getAttributeValue(null, "date1904");
                        date1904 = "1".equals(value) || "true".equalsIgnoreCase(value);
                    }
                    case "sheet" -> sheets.add(new SheetRef(
                            reader.getAttributeValue(null, "name"),
                            reader.getAttributeValue(MAIN_NS_REL, "id")));
                    default -> {
                    }
                }
            }
        } catch (XMLStreamException ex) {
            throw broken();
        }
        return new Workbook(sheets, date1904);
    }

    private record Relationship(String id, String target, String type) {
    }

    /** The workbook's links to its sheets, shared strings and styles. */
    private static List<Relationship> parseRelationships(byte[] xml) throws XlsxException {
        List<Relationship> relationships = new ArrayList<>();
        if (xml == null) {
            return relationships;
        }
        XMLStreamReader reader = open(xml);
        try {
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT && "Relationship".equals(reader.getLocalName())) {
                    String target = reader.getAttributeValue(null, "Target");
                    if (target != null) {
                        relationships.add(new Relationship(reader.getAttributeValue(null, "Id"), resolve(target),
                                String.valueOf(reader.getAttributeValue(null, "Type"))));
                    }
                }
            }
        } catch (XMLStreamException ex) {
            throw broken();
        }
        return relationships;
    }

    /** Finds a workbook-level part by relationship type, falling back to its usual name. */
    private static String findPart(Map<String, byte[]> parts, List<Relationship> relationships, String typeSuffix,
                                   String fallback) {
        return relationships.stream()
                .filter(relationship -> relationship.type().endsWith("/" + typeSuffix))
                .map(Relationship::target)
                .filter(parts::containsKey)
                .findFirst()
                .orElse(fallback);
    }

    /** Targets are relative to xl/ unless they start with a slash. */
    private static String resolve(String target) {
        if (target.startsWith("/")) {
            return stripLeadingSlash(target);
        }
        String path = "xl/" + target;
        // Normalise the odd "../" some writers use.
        while (path.contains("/../")) {
            path = path.replaceFirst("[^/]+/\\.\\./", "");
        }
        return path;
    }

    private static String stripLeadingSlash(String name) {
        return name.startsWith("/") ? name.substring(1) : name;
    }

    // ============ shared strings ============

    private static List<String> parseSharedStrings(byte[] xml) throws XlsxException {
        List<String> strings = new ArrayList<>();
        if (xml == null) {
            return strings;
        }
        XMLStreamReader reader = open(xml);
        try {
            StringBuilder current = null;
            int phonetic = 0;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "si" -> current = new StringBuilder();
                        case "rPh" -> phonetic++;
                        case "t" -> {
                            String text = reader.getElementText();
                            if (current != null && phonetic == 0) {
                                current.append(text);
                            }
                        }
                        default -> {
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if ("si".equals(reader.getLocalName()) && current != null) {
                        strings.add(current.toString());
                        current = null;
                    } else if ("rPh".equals(reader.getLocalName())) {
                        phonetic--;
                    }
                }
            }
        } catch (XMLStreamException ex) {
            throw broken();
        }
        return strings;
    }

    // ============ styles ============

    /** Per cell style index: whether its number format shows a date, a percentage, or neither. */
    private record Styles(List<XlsxCell.Kind> numberKinds) {
        XlsxCell.Kind kindFor(int styleIndex) {
            return styleIndex >= 0 && styleIndex < numberKinds.size()
                    ? numberKinds.get(styleIndex) : XlsxCell.Kind.NUMBER;
        }
    }

    private static Styles parseStyles(byte[] xml) throws XlsxException {
        List<XlsxCell.Kind> kinds = new ArrayList<>();
        if (xml == null) {
            return new Styles(kinds);
        }
        Map<Integer, String> customFormats = new HashMap<>();
        XMLStreamReader reader = open(xml);
        try {
            boolean inCellXfs = false;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "numFmt" -> customFormats.put(
                                parseIntOr(reader.getAttributeValue(null, "numFmtId"), -1),
                                reader.getAttributeValue(null, "formatCode"));
                        case "cellXfs" -> inCellXfs = true;
                        case "xf" -> {
                            if (inCellXfs) {
                                int id = parseIntOr(reader.getAttributeValue(null, "numFmtId"), 0);
                                kinds.add(numberKind(id, customFormats.get(id)));
                            }
                        }
                        default -> {
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT && "cellXfs".equals(reader.getLocalName())) {
                    inCellXfs = false;
                }
            }
        } catch (XMLStreamException ex) {
            throw broken();
        }
        return new Styles(kinds);
    }

    static XlsxCell.Kind numberKind(int formatId, String formatCode) {
        if (formatId == 9 || formatId == 10) {
            return XlsxCell.Kind.PERCENT;
        }
        if ((formatId >= 14 && formatId <= 22) || (formatId >= 27 && formatId <= 36)
                || (formatId >= 45 && formatId <= 47) || (formatId >= 50 && formatId <= 58)) {
            return XlsxCell.Kind.DATE;
        }
        if (formatCode == null) {
            return XlsxCell.Kind.NUMBER;
        }
        // Quoted literals, escaped characters and [colour]/[locale] blocks aren't format letters.
        String code = formatCode.replaceAll("\"[^\"]*\"", "")
                .replaceAll("\\\\.", "")
                .replaceAll("\\[[^]]*]", "")
                .toLowerCase();
        if (code.contains("%")) {
            return XlsxCell.Kind.PERCENT;
        }
        if (code.matches(".*[dmyhs].*") && !code.equals("general")) {
            return XlsxCell.Kind.DATE;
        }
        return XlsxCell.Kind.NUMBER;
    }

    // ============ sheets ============

    private static List<XlsxRow> parseSheet(String sheetName, byte[] xml, List<String> strings, Styles styles,
                                            boolean date1904) throws XlsxException {
        List<XlsxRow> rows = new ArrayList<>();
        XMLStreamReader reader = open(xml);
        try {
            int rowNumber = 0;
            int nextColumn = 0;
            Map<Integer, XlsxCell> cells = null;

            String cellType = null;
            int cellStyle = 0;
            int cellColumn = 0;
            String value = null;
            StringBuilder inline = null;

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "row" -> {
                            rowNumber = parseIntOr(reader.getAttributeValue(null, "r"), rowNumber + 1);
                            if (rowNumber > MAX_ROWS_PER_SHEET + 1) {
                                throw new XlsxException("Sheet \"" + sheetName + "\" has more than "
                                        + MAX_ROWS_PER_SHEET + " rows. Split it into smaller files.");
                            }
                            cells = new LinkedHashMap<>();
                            nextColumn = 0;
                        }
                        case "c" -> {
                            String ref = reader.getAttributeValue(null, "r");
                            cellColumn = ref == null ? nextColumn : columnIndex(ref, nextColumn);
                            nextColumn = cellColumn + 1;
                            cellType = reader.getAttributeValue(null, "t");
                            cellStyle = parseIntOr(reader.getAttributeValue(null, "s"), 0);
                            value = null;
                            inline = null;
                        }
                        case "v" -> value = reader.getElementText();
                        case "is" -> inline = new StringBuilder();
                        case "t" -> {
                            String text = reader.getElementText();
                            if (inline != null) {
                                inline.append(text);
                            }
                        }
                        default -> {
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    switch (reader.getLocalName()) {
                        case "c" -> {
                            if (cells != null && cellColumn < MAX_COLUMNS) {
                                XlsxCell cell = toCell(cellType, cellStyle, value, inline, strings, styles, date1904);
                                if (cell != null && !cell.isBlank()) {
                                    cells.put(cellColumn, cell);
                                }
                            }
                        }
                        case "row" -> {
                            if (cells != null && !cells.isEmpty()) {
                                rows.add(new XlsxRow(rowNumber, cells));
                            }
                            cells = null;
                        }
                        default -> {
                        }
                    }
                }
            }
        } catch (XMLStreamException ex) {
            throw broken();
        }
        return rows;
    }

    private static XlsxCell toCell(String type, int style, String value, StringBuilder inline,
                                   List<String> strings, Styles styles, boolean date1904) {
        if (type == null || type.equals("n")) {
            if (value == null || value.isBlank()) {
                return null;
            }
            XlsxCell.Kind kind = styles.kindFor(style);
            return XlsxCell.number(value.trim(), kind, date1904);
        }
        return switch (type) {
            case "s" -> {
                int index = parseIntOr(value, -1);
                yield index >= 0 && index < strings.size() ? XlsxCell.text(strings.get(index)) : null;
            }
            case "inlineStr" -> inline == null ? null : XlsxCell.text(inline.toString());
            case "str" -> value == null ? null : XlsxCell.text(value);
            case "b" -> value == null ? null : XlsxCell.bool("1".equals(value.trim()));
            case "e" -> XlsxCell.error(value == null ? "#ERROR" : value);
            case "d" -> value == null ? null : XlsxCell.isoDate(value.trim());
            default -> value == null ? null : XlsxCell.text(value);
        };
    }

    /** "C12" is column 2 (zero-based). Returns the fallback if the reference is malformed. */
    static int columnIndex(String ref, int fallback) {
        var matcher = CELL_REF.matcher(ref.toUpperCase());
        if (!matcher.matches()) {
            return fallback;
        }
        int column = 0;
        for (char letter : matcher.group(1).toCharArray()) {
            column = column * 26 + (letter - 'A' + 1);
        }
        return column - 1;
    }

    // ============ helpers ============

    private static XMLStreamReader open(byte[] xml) throws XlsxException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        try {
            return factory.createXMLStreamReader(new ByteArrayInputStream(xml));
        } catch (XMLStreamException ex) {
            throw broken();
        }
    }

    private static int parseIntOr(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static XlsxException broken() {
        return new XlsxException("The workbook looks damaged. Open it in Excel, save it again as .xlsx, and retry.");
    }
}
