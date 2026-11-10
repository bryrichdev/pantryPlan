package edu.wgu.pantryprep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryprep.domain.RecipeIcon;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.importing.ImportWorkbooks;
import edu.wgu.pantryprep.importing.ParsedRow;
import edu.wgu.pantryprep.importing.Problems;
import edu.wgu.pantryprep.importing.SheetParser;
import edu.wgu.pantryprep.importing.ValueType;
import edu.wgu.pantryprep.importing.WorkbookSpec;
import edu.wgu.pantryprep.importing.WorkbookSpec.Sheet;
import edu.wgu.pantryprep.importing.xlsx.XlsxCell;
import edu.wgu.pantryprep.importing.xlsx.XlsxReader;
import edu.wgu.pantryprep.importing.xlsx.XlsxSheet;
import edu.wgu.pantryprep.importing.xlsx.XlsxWorkbook;
import edu.wgu.pantryprep.importing.xlsx.XlsxWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

/** The downloadable templates and the cell rules behind them, without a database. */
class SpreadsheetTemplateTests {

    @Test
    void eachTemplateReadsBackWithItsSheetsHeadersAndNoRows() throws Exception {
        for (WorkbookSpec spec : List.of(ImportWorkbooks.RECIPE_WORKBOOK, ImportWorkbooks.PANTRY_WORKBOOK)) {
            XlsxWorkbook workbook = XlsxReader.read(new ByteArrayInputStream(spec.template()));
            List<String> names = workbook.sheets().stream().map(XlsxSheet::name).toList();
            assertEquals(WorkbookSpec.INSTRUCTIONS, names.getFirst(), "instructions come first");
            for (Sheet sheet : spec.sheets()) {
                assertTrue(names.contains(sheet.name()), spec.title() + " has " + sheet.name());
            }

            Problems problems = new Problems(spec);
            Map<Sheet, List<ParsedRow>> rows = SheetParser.parse(spec, workbook, problems);
            assertTrue(problems.isEmpty(), "an untouched template reads cleanly: " + problems.sorted());
            rows.values().forEach(sheetRows -> assertTrue(sheetRows.isEmpty()));
        }
    }

    @Test
    void everyDropdownFitsWhereExcelLooksForIt() throws Exception {
        for (WorkbookSpec spec : List.of(ImportWorkbooks.RECIPE_WORKBOOK, ImportWorkbooks.PANTRY_WORKBOOK)) {
            String xml = allSheetXml(spec.template());
            Matcher formulas = Pattern.compile("<formula1>(.*?)</formula1>").matcher(xml);
            int found = 0;
            while (formulas.find()) {
                found++;
                String formula = formulas.group(1).replace("&quot;", "\"").replace("&amp;", "&");
                assertTrue(formula.length() <= 255, "Excel refuses a list over 255 characters: " + formula);
            }
            assertTrue(found > 0, spec.title() + " offers dropdowns");
        }
        assertTrue(allSheetXml(ImportWorkbooks.RECIPE_WORKBOOK.template()).contains(RecipeIcon.WRAP.getLabel()),
                "every picture is offered");
    }

    @Test
    void aListTooLongToWriteInlineMovesToAHiddenSheet() throws Exception {
        List<String> many = java.util.stream.IntStream.rangeClosed(1, 60).mapToObj(i -> "Choice number " + i).toList();
        byte[] xlsx = XlsxWriter.write(List.of(new XlsxWriter.TableSheet("Data", List.of(
                new XlsxWriter.Column("Pick", 12, false, XlsxWriter.Format.GENERAL, many)))));
        String xml = allSheetXml(xlsx);
        assertTrue(xml.contains("<formula1>Lists!$A$1:$A$60</formula1>"), xml);
        assertTrue(xml.contains("name=\"Lists\" sheetId=\"2\" state=\"hidden\""), "hidden from the cook");
        assertTrue(xml.contains("Choice number 60"));

        XlsxWorkbook back = XlsxReader.read(new ByteArrayInputStream(xlsx));
        assertEquals(2, back.sheets().size());
        WorkbookSpec spec = new WorkbookSpec("t", "t.xlsx", List.of(), List.of(new Sheet("Data", "", List.of(
                WorkbookSpec.optional("pick", "Pick", ValueType.oneOf(many), 12, null)))));
        Problems problems = new Problems(spec);
        SheetParser.parse(spec, back, problems);
        assertTrue(problems.isEmpty(), "the lists sheet isn't mistaken for data: " + problems.sorted());
    }

    @Test
    void amountsAcceptDecimalsAndKitchenFractions() throws Exception {
        ValueType amount = ValueType.amount(false);
        assertEquals(0, new BigDecimal("2").compareTo((BigDecimal) amount.convert(XlsxCell.text("2"))));
        assertEquals(0, new BigDecimal("1.5").compareTo((BigDecimal) amount.convert(XlsxCell.text("1.5"))));
        assertEquals(0, new BigDecimal("0.5").compareTo((BigDecimal) amount.convert(XlsxCell.text("1/2"))));
        assertEquals(0, new BigDecimal("1.5").compareTo((BigDecimal) amount.convert(XlsxCell.text("1 1/2"))));
        assertEquals(0, new BigDecimal("0.333").compareTo((BigDecimal) amount.convert(XlsxCell.text("1/3"))));
        assertThrows(ValueType.BadValue.class, () -> amount.convert(XlsxCell.text("0")), "a recipe needs some");
        assertThrows(ValueType.BadValue.class, () -> amount.convert(XlsxCell.text("a pinch")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) ValueType.amount(true).convert(XlsxCell.text("0"))),
                "an empty jar can still be on the shelf");
    }

    @Test
    void unitsMatchTheirShortNamesAndTheWordsPeopleWrite() throws Exception {
        ValueType unit = ImportWorkbooks.RECIPE_INGREDIENTS.column(ImportWorkbooks.UNIT).orElseThrow().type();
        assertEquals(Unit.CUP, unit.convert(XlsxCell.text("cups")));
        assertEquals(Unit.TABLESPOON, unit.convert(XlsxCell.text("Tbsp")));
        assertEquals(Unit.TEASPOON, unit.convert(XlsxCell.text("teaspoons")));
        assertEquals(Unit.LITER, unit.convert(XlsxCell.text("l")));
        assertEquals(Unit.GRAM, unit.convert(XlsxCell.text("grams")));
        assertEquals(Unit.PIECE, unit.convert(XlsxCell.text("each")));
        assertThrows(ValueType.BadValue.class, () -> unit.convert(XlsxCell.text("handful")));
    }

    @Test
    void picturesAreChosenByTheirLabel() throws Exception {
        ValueType picture = ImportWorkbooks.RECIPES.column(ImportWorkbooks.PICTURE).orElseThrow().type();
        assertEquals(RecipeIcon.WRAP, picture.convert(XlsxCell.text("Wrap or burrito")));
        assertEquals(RecipeIcon.POT, picture.convert(XlsxCell.text("soup pot")));
        assertFalse(picture.choices().isEmpty());
    }

    private static String allSheetXml(byte[] xlsx) throws Exception {
        StringBuilder xml = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().startsWith("xl/worksheets/") || entry.getName().equals("xl/workbook.xml")) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    zip.transferTo(out);
                    xml.append(out.toString(StandardCharsets.UTF_8));
                }
            }
        }
        return xml.toString();
    }
}
