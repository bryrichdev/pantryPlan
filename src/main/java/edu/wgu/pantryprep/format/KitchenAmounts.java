package edu.wgu.pantryprep.format;

import edu.wgu.pantryprep.domain.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Amounts the way a cook says them: "1 lb 8 oz" rather than 1.5 lb, "750 g"
 * rather than 0.75 kg, "¾ cup 1 tbsp" rather than 0.8125 cup, "½ pc" rather
 * than 0.5 pc.
 *
 * <p>Only the text changes. Amounts are stored and converted at full
 * precision, so nothing here feeds back into a calculation.
 *
 * <ul>
 *   <li>Pounds and ounces: whole ounces, as pounds and ounces from 16 up.
 *       Under 4 oz, halves are kept so a small spice jar is not rounded away.</li>
 *   <li>Metric weight and volume: whole grams or millilitres below 1000,
 *       kilograms or litres to two places from there. Under 10, one decimal
 *       place is kept for small amounts like saffron.</li>
 *   <li>Cups, tablespoons, teaspoons: combined into the measuring-set units a
 *       kitchen has — cups with ¼ ⅓ ½ ⅔ ¾, whole tablespoons, and teaspoons in
 *       eighths. The finer parts are rounded more coarsely as the total grows.</li>
 *   <li>Pieces: whole numbers with ¼ ⅓ ½ ⅔ ¾.</li>
 * </ul>
 */
public final class KitchenAmounts {

    private static final BigDecimal SIXTEEN = BigDecimal.valueOf(16);
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final BigDecimal FOUR_OUNCES = BigDecimal.valueOf(4);

    /* US volume is counted in eighths of a teaspoon. */
    private static final int TSP = 8;
    private static final int TBSP = 3 * TSP;
    private static final int CUP = 16 * TBSP;

    /* The cup fractions a measuring set has, largest first. */
    private static final int[] CUP_PARTS = {CUP * 3 / 4, CUP * 2 / 3, CUP / 2, CUP / 3, CUP / 4};
    private static final String[] CUP_GLYPHS = {"¾", "⅔", "½", "⅓", "¼"};

    private static final String[] TSP_EIGHTHS = {"", "⅛", "¼", "⅜", "½", "⅝", "¾", "⅞"};

    /* Fractions of a whole piece, with their glyphs, smallest first. */
    private static final double[] PIECE_PARTS = {0, 0.25, 1.0 / 3, 0.5, 2.0 / 3, 0.75, 1};
    private static final String[] PIECE_GLYPHS = {"", "¼", "⅓", "½", "⅔", "¾", ""};

    private KitchenAmounts() {
    }

    public static String format(BigDecimal value, Unit unit) {
        if (value == null || unit == null) {
            return "";
        }
        if (value.signum() < 0) {
            return "-" + format(value.negate(), unit);
        }
        if (value.signum() == 0) {
            return "0 " + unit.getAbbreviation();
        }
        return switch (unit) {
            case POUND -> ounces(value.multiply(SIXTEEN));
            case OUNCE -> ounces(value);
            case KILOGRAM -> metric(value.multiply(THOUSAND), "g", "kg");
            case GRAM -> metric(value, "g", "kg");
            case LITER -> metric(value.multiply(THOUSAND), "ml", "L");
            case MILLILITER -> metric(value, "ml", "L");
            case CUP -> usVolume(value.multiply(BigDecimal.valueOf(CUP)));
            case TABLESPOON -> usVolume(value.multiply(BigDecimal.valueOf(TBSP)));
            case TEASPOON -> usVolume(value.multiply(BigDecimal.valueOf(TSP)));
            case PIECE -> pieces(value);
        };
    }

    private static String ounces(BigDecimal ounces) {
        if (ounces.compareTo(FOUR_OUNCES) < 0) {
            long halves = ounces.multiply(BigDecimal.valueOf(2)).setScale(0, RoundingMode.HALF_UP).longValue();
            if (halves == 0) {
                return "< ½ oz";
            }
            return mixed(halves / 2, halves % 2 == 1 ? "½" : "") + " oz";
        }
        long total = ounces.setScale(0, RoundingMode.HALF_UP).longValue();
        long pounds = total / 16;
        long rest = total % 16;
        if (pounds == 0) {
            return rest + " oz";
        }
        return rest == 0 ? pounds + " lb" : pounds + " lb " + rest + " oz";
    }

    private static String metric(BigDecimal small, String smallUnit, String largeUnit) {
        if (small.compareTo(TEN) < 0) {
            BigDecimal tenths = small.setScale(1, RoundingMode.HALF_UP);
            if (tenths.signum() == 0) {
                return "< 0.1 " + smallUnit;
            }
            return plain(tenths) + " " + smallUnit;
        }
        BigDecimal whole = small.setScale(0, RoundingMode.HALF_UP);
        if (whole.compareTo(THOUSAND) < 0) {
            return whole.toPlainString() + " " + smallUnit;
        }
        return plain(small.divide(THOUSAND).setScale(2, RoundingMode.HALF_UP)) + " " + largeUnit;
    }

    private static String usVolume(BigDecimal eighthsOfATeaspoon) {
        long total = eighthsOfATeaspoon.setScale(0, RoundingMode.HALF_UP).longValue();
        if (total == 0) {
            return "< ⅛ tsp";
        }
        /* Past a quarter cup, half teaspoons are plenty; past a tablespoon, quarters. */
        if (total >= CUP / 4) {
            total = roundTo(total, TSP / 2);
        } else if (total >= TBSP) {
            total = roundTo(total, TSP / 4);
        }

        List<String> parts = new ArrayList<>();
        long cups = total / CUP;
        long rest = total % CUP;
        String cupGlyph = "";
        for (int i = 0; i < CUP_PARTS.length; i++) {
            if (rest >= CUP_PARTS[i]) {
                cupGlyph = CUP_GLYPHS[i];
                rest -= CUP_PARTS[i];
                break;
            }
        }
        if (cups > 0 || !cupGlyph.isEmpty()) {
            boolean plural = cups > 1 || (cups == 1 && !cupGlyph.isEmpty());
            parts.add(mixed(cups, cupGlyph) + (plural ? " cups" : " cup"));
        }

        long tablespoons = rest / TBSP;
        rest %= TBSP;
        if (tablespoons > 0) {
            parts.add(tablespoons + " tbsp");
        }

        long teaspoons = rest / TSP;
        int eighths = (int) (rest % TSP);
        if (teaspoons > 0 || eighths > 0) {
            parts.add(mixed(teaspoons, TSP_EIGHTHS[eighths]) + " tsp");
        }
        return String.join(" ", parts);
    }

    private static String pieces(BigDecimal value) {
        long whole = value.setScale(0, RoundingMode.DOWN).longValue();
        double fraction = value.subtract(BigDecimal.valueOf(whole)).doubleValue();

        int nearest = 0;
        for (int i = 1; i < PIECE_PARTS.length; i++) {
            if (Math.abs(fraction - PIECE_PARTS[i]) < Math.abs(fraction - PIECE_PARTS[nearest])) {
                nearest = i;
            }
        }
        if (nearest == PIECE_PARTS.length - 1) {
            whole++;
        }
        String glyph = PIECE_GLYPHS[nearest];
        if (whole == 0 && glyph.isEmpty()) {
            return "< ¼ pc";
        }
        return mixed(whole, glyph) + " pc";
    }

    /** 1 and "½" become "1½"; 0 and "½" become "½"; 2 and "" become "2". */
    private static String mixed(long whole, String glyph) {
        if (glyph.isEmpty()) {
            return Long.toString(whole);
        }
        return whole == 0 ? glyph : whole + glyph;
    }

    private static long roundTo(long value, int step) {
        return (value + step / 2) / step * step;
    }

    private static String plain(BigDecimal value) {
        BigDecimal trimmed = value.stripTrailingZeros();
        return trimmed.scale() < 0 ? trimmed.setScale(0).toPlainString() : trimmed.toPlainString();
    }
}
