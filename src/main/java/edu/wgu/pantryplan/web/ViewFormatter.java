package edu.wgu.pantryplan.web;

import edu.wgu.pantryplan.domain.Unit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Display helpers callable from Thymeleaf as {@code ${@fmt.date(...)}}.
 *
 * <p>Keeping formatting here rather than in the entities leaves the domain
 * classes free of presentation concerns.
 */
@Component("fmt")
public class ViewFormatter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a");

    public String date(Instant instant) {
        return instant == null ? "" : DATE.format(instant.atZone(ZoneId.systemDefault()));
    }

    public String date(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    public String dateTime(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant.atZone(ZoneId.systemDefault()));
    }

    /**
     * Trims trailing zeros so 2.000 shows as 2 while 1.500 stays 1.5.
     */
    public String quantity(BigDecimal value) {
        if (value == null) {
            return "";
        }
        BigDecimal trimmed = value.stripTrailingZeros();
        return trimmed.scale() < 0 ? trimmed.setScale(0).toPlainString() : trimmed.toPlainString();
    }

    public String amount(BigDecimal value, Unit unit) {
        if (value == null || unit == null) {
            return "";
        }
        return quantity(value) + " " + unit.getAbbreviation();
    }

    /**
     * PANTRY_STAPLE becomes "Pantry staple".
     */
    public String label(Enum<?> value) {
        if (value == null) {
            return "";
        }
        String words = value.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
