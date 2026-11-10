package edu.wgu.pantryprep.importing.xlsx;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * One cell's value as stored. A number keeps what its format said it was: a date, a
 * percentage, or a plain number. Excel keeps a date as the count of days since 1900 (or
 * 1904 on some older Macs), shown as a date only by its format.
 */
public record XlsxCell(Kind kind, String text, BigDecimal number, LocalDate date) {

    public enum Kind {
        TEXT, NUMBER, DATE, PERCENT, BOOLEAN, ERROR
    }

    private static final LocalDate EPOCH_1900 = LocalDate.of(1899, 12, 30);
    private static final LocalDate EPOCH_1904 = LocalDate.of(1904, 1, 1);

    public static XlsxCell text(String value) {
        return new XlsxCell(Kind.TEXT, value, null, null);
    }

    public static XlsxCell bool(boolean value) {
        return new XlsxCell(Kind.BOOLEAN, value ? "TRUE" : "FALSE", null, null);
    }

    public static XlsxCell error(String code) {
        return new XlsxCell(Kind.ERROR, code, null, null);
    }

    static XlsxCell number(String raw, Kind kind, boolean date1904) {
        BigDecimal value;
        try {
            value = new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            return text(raw);
        }
        if (kind == Kind.DATE) {
            LocalDate date = serialToDate(value, date1904);
            return date == null ? new XlsxCell(Kind.NUMBER, plain(value), value, null)
                    : new XlsxCell(Kind.DATE, date.toString(), value, date);
        }
        return new XlsxCell(kind == Kind.PERCENT ? Kind.PERCENT : Kind.NUMBER, plain(value), value, null);
    }

    /** A cell stored with t="d": an ISO 8601 date, possibly with a time after it. */
    static XlsxCell isoDate(String value) {
        try {
            LocalDate date = LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
            return new XlsxCell(Kind.DATE, date.toString(), null, date);
        } catch (DateTimeParseException ex) {
            return text(value);
        }
    }

    /** Day serial to date. The time of day, if any, is dropped. */
    public static LocalDate serialToDate(BigDecimal serial, boolean date1904) {
        if (serial.signum() < 0 || serial.compareTo(BigDecimal.valueOf(2_958_465)) > 0) {
            return null;
        }
        long days = serial.longValue();
        return (date1904 ? EPOCH_1904 : EPOCH_1900).plusDays(days);
    }

    /** 1234567890 rather than 1.23456789E+9, and 25 rather than 25.0. */
    private static String plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0).toPlainString() : stripped.toPlainString();
    }

    public boolean isBlank() {
        return kind == Kind.TEXT && (text == null || text.isBlank());
    }
}
