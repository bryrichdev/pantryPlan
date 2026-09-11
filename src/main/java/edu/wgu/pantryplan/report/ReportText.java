package edu.wgu.pantryplan.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Formatting shared by report rows before they reach the generic template. */
final class ReportText {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private ReportText() {
    }

    static String quantity(BigDecimal quantity) {
        BigDecimal trimmed = quantity.stripTrailingZeros();
        return trimmed.scale() < 0 ? trimmed.setScale(0).toPlainString() : trimmed.toPlainString();
    }

    static String label(Enum<?> value) {
        String words = value.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    static String date(LocalDate value) {
        return DATE.format(value);
    }
}
