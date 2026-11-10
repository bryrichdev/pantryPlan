package edu.wgu.pantryprep.importing.xlsx;

/** The upload can't be read as a workbook at all. The message is written for the person uploading. */
public class XlsxException extends Exception {

    public XlsxException(String message) {
        super(message);
    }
}
