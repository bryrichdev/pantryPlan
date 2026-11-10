package edu.wgu.pantryprep.importing;

import edu.wgu.pantryprep.importing.xlsx.XlsxCell;
import edu.wgu.pantryprep.importing.xlsx.XlsxWriter.Format;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a spreadsheet column holds: how a cell becomes a value, how the template
 * shows the column, and how the instructions sheet describes it. Forgiving about
 * how people type into Excel (1 1/2, "cups", 3/14/2026) and strict about what it
 * produces, so a row that passes here passes the same rules as the forms.
 */
public abstract class ValueType {

    /** A cell that can't be read as this type; the message is shown against its row. */
    public static class BadValue extends Exception {
        public BadValue(String message) {
            super(message);
        }
    }

    /** The largest amount the database column holds. */
    static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999.999");

    public abstract Object convert(XlsxCell cell) throws BadValue;

    public abstract String describe();

    public Format format() {
        return Format.GENERAL;
    }

    public List<String> choices() {
        return List.of();
    }

    // ------------------------------------------------------------------ types

    public static ValueType text(int maxLength) {
        return new ValueType() {
            @Override
            public Object convert(XlsxCell cell) throws BadValue {
                String text = plainText(cell);
                if (text.length() > maxLength) {
                    throw new BadValue("Keep this to " + maxLength + " characters or fewer");
                }
                return text;
            }

            @Override
            public String describe() {
                return "Text";
            }
        };
    }

    /** A whole number between min and max, such as servings or minutes. */
    public static ValueType whole(int min, int max) {
        return new ValueType() {
            @Override
            public Object convert(XlsxCell cell) throws BadValue {
                BigDecimal value = number(cell, "a whole number");
                if (value.stripTrailingZeros().scale() > 0) {
                    throw new BadValue("Use a whole number, not " + plainText(cell));
                }
                if (value.compareTo(BigDecimal.valueOf(min)) < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0) {
                    throw new BadValue("Use a number from " + min + " to " + max);
                }
                return value.intValueExact();
            }

            @Override
            public String describe() {
                return "A whole number from " + min + " to " + max;
            }
        };
    }

    /**
     * An amount of an ingredient: 2, 1.5, 1/2 or 1 1/2. Kept as text in the
     * template, because Excel turns a typed 1/2 into the 2nd of January.
     * Fractions are rounded to three decimal places, the precision stored.
     */
    public static ValueType amount(boolean allowZero) {
        return new ValueType() {
            @Override
            public Object convert(XlsxCell cell) throws BadValue {
                BigDecimal value = parseAmount(cell);
                if (value.signum() < 0 || (!allowZero && value.signum() == 0)) {
                    throw new BadValue(allowZero ? "The amount can't be negative" : "The amount must be more than zero");
                }
                if (value.compareTo(MAX_AMOUNT) > 0) {
                    throw new BadValue("That amount is too large");
                }
                return value;
            }

            @Override
            public String describe() {
                return "A number such as 2, 1.5, 1/2 or 1 1/2";
            }

            @Override
            public Format format() {
                return Format.TEXT;
            }
        };
    }

    public static final ValueType DATE = new ValueType() {
        private final List<DateTimeFormatter> formats = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("M/d/uuuu").withResolverStyle(ResolverStyle.STRICT),
                DateTimeFormatter.ofPattern("M-d-uuuu").withResolverStyle(ResolverStyle.STRICT),
                DateTimeFormatter.ofPattern("uuuu/M/d").withResolverStyle(ResolverStyle.STRICT));

        @Override
        public Object convert(XlsxCell cell) throws BadValue {
            if (cell.date() != null) {
                return cell.date();
            }
            if (cell.kind() == XlsxCell.Kind.NUMBER && cell.number() != null) {
                // A date typed into a cell that lost its date format is still a day count.
                LocalDate date = XlsxCell.serialToDate(cell.number(), false);
                if (date != null && date.getYear() >= 1900) {
                    return date;
                }
            }
            String text = plainText(cell);
            for (DateTimeFormatter format : formats) {
                try {
                    return LocalDate.parse(text, format);
                } catch (DateTimeParseException ignored) {
                    // try the next one
                }
            }
            throw new BadValue("\"" + text + "\" isn't a date. Use 2026-01-31 or 1/31/2026");
        }

        @Override
        public String describe() {
            return "A date, such as 2026-01-31";
        }

        @Override
        public Format format() {
            return Format.DATE;
        }
    };

    /**
     * One of a fixed set of enum values. labels are what the dropdown offers,
     * in order; aliases are other spellings people use. Matching ignores case,
     * spaces and punctuation.
     */
    public static <E extends Enum<E>> ValueType choice(Class<E> type, Function<E, String> label,
                                                       Map<String, E> aliases) {
        Map<String, Object> byName = new LinkedHashMap<>();
        List<String> labels = new ArrayList<>();
        for (E constant : type.getEnumConstants()) {
            labels.add(label.apply(constant));
            byName.put(normalize(label.apply(constant)), constant);
            byName.put(normalize(constant.name()), constant);
        }
        aliases.forEach((alias, constant) -> byName.put(normalize(alias), constant));
        return choice(labels, byName);
    }

    public static <E extends Enum<E>> ValueType choice(Class<E> type) {
        return choice(type, ValueType::titleCase, Map.of());
    }

    /** One of a fixed list of text values; the canonical spelling is returned. */
    public static ValueType oneOf(List<String> values) {
        Map<String, Object> byName = new LinkedHashMap<>();
        values.forEach(value -> byName.put(normalize(value), value));
        return choice(values, byName);
    }

    private static ValueType choice(List<String> labels, Map<String, Object> byName) {
        return new ValueType() {
            @Override
            public Object convert(XlsxCell cell) throws BadValue {
                Object match = byName.get(normalize(plainText(cell)));
                if (match == null) {
                    throw new BadValue("\"" + plainText(cell) + "\" isn't one of: " + String.join(", ", labels));
                }
                return match;
            }

            @Override
            public String describe() {
                return "One of: " + String.join(", ", labels);
            }

            @Override
            public List<String> choices() {
                return labels;
            }
        };
    }

    // ---------------------------------------------------------------- helpers

    /** Production, Pantry staple, Spice rack: the way the app labels enum values. */
    public static String titleCase(Enum<?> constant) {
        String words = constant.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** The cell as text: numbers without a trailing .0, and never an Excel error code. */
    static String plainText(XlsxCell cell) throws BadValue {
        if (cell.kind() == XlsxCell.Kind.ERROR) {
            throw new BadValue("The cell shows an Excel error (" + cell.text() + ")");
        }
        return cell.text().strip();
    }

    private static BigDecimal number(XlsxCell cell, String what) throws BadValue {
        if (cell.number() != null && cell.kind() != XlsxCell.Kind.DATE) {
            return cell.number();
        }
        String text = plainText(cell).replace(",", "");
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            throw new BadValue("\"" + plainText(cell) + "\" isn't " + what);
        }
    }

    private static final Pattern MIXED = Pattern.compile("^(\\d+)\\s+(\\d+)\\s*/\\s*(\\d+)$");
    private static final Pattern FRACTION = Pattern.compile("^(\\d+)\\s*/\\s*(\\d+)$");
    private static final Map<Character, String> VULGAR = Map.of(
            '½', "1/2", '⅓', "1/3", '⅔', "2/3", '¼', "1/4", '¾', "3/4", '⅛', "1/8");

    static BigDecimal parseAmount(XlsxCell cell) throws BadValue {
        if (cell.kind() == XlsxCell.Kind.DATE) {
            throw new BadValue("Excel turned this amount into a date. Type it as 0.5 rather than 1/2,"
                    + " or format the column as text");
        }
        if (cell.number() != null) {
            return checkScale(cell.number(), cell);
        }
        String text = plainText(cell);
        for (Map.Entry<Character, String> entry : VULGAR.entrySet()) {
            int at = text.indexOf(entry.getKey());
            if (at >= 0) {
                String before = text.substring(0, at).strip();
                text = (before.isEmpty() ? "" : before + " ") + entry.getValue();
            }
        }
        Matcher mixed = MIXED.matcher(text);
        if (mixed.matches()) {
            return new BigDecimal(mixed.group(1)).add(fraction(mixed.group(2), mixed.group(3), cell));
        }
        Matcher fraction = FRACTION.matcher(text);
        if (fraction.matches()) {
            return fraction(fraction.group(1), fraction.group(2), cell);
        }
        try {
            return checkScale(new BigDecimal(text.replace(",", "")), cell);
        } catch (NumberFormatException ex) {
            throw new BadValue("\"" + text + "\" isn't an amount. Use a number such as 2, 1.5 or 1 1/2");
        }
    }

    private static BigDecimal fraction(String top, String bottom, XlsxCell cell) throws BadValue {
        BigDecimal denominator = new BigDecimal(bottom);
        if (denominator.signum() == 0) {
            throw new BadValue("\"" + plainText(cell) + "\" divides by zero");
        }
        return new BigDecimal(top).divide(denominator, 3, RoundingMode.HALF_UP);
    }

    private static BigDecimal checkScale(BigDecimal value, XlsxCell cell) throws BadValue {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() > 3) {
            throw new BadValue("Use up to three decimal places, not " + plainText(cell));
        }
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
