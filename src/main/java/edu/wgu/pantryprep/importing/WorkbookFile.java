package edu.wgu.pantryprep.importing;

import edu.wgu.pantryprep.importing.WorkbookSpec.Sheet;
import edu.wgu.pantryprep.importing.xlsx.XlsxException;
import edu.wgu.pantryprep.importing.xlsx.XlsxReader;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

/** Opens an uploaded file against a spec: rows by sheet, or problems saying why not. */
final class WorkbookFile {

    private WorkbookFile() {
    }

    /** Null when the file can't be read at all; the reason is in problems. */
    static Map<Sheet, List<ParsedRow>> read(WorkbookSpec spec, byte[] bytes, Problems problems) {
        if (bytes == null || bytes.length == 0) {
            problems.file("The file is empty. Choose the filled-in spreadsheet and upload it again.");
            return null;
        }
        try {
            return SheetParser.parse(spec, XlsxReader.read(new ByteArrayInputStream(bytes)), problems);
        } catch (XlsxException ex) {
            problems.file(ex.getMessage());
            return null;
        }
    }
}
