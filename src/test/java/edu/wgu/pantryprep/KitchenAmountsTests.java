package edu.wgu.pantryprep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.format.KitchenAmounts;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Amounts read the way a cook says them. */
class KitchenAmountsTests {

    private static String say(String value, Unit unit) {
        return KitchenAmounts.format(new BigDecimal(value), unit);
    }

    @Test
    void poundsBecomePoundsAndOunces() {
        assertEquals("1 lb 8 oz", say("1.500", Unit.POUND));
        assertEquals("2 lb", say("2.000", Unit.POUND));
        assertEquals("5 oz", say("0.323", Unit.POUND), "the grocery list's 0.323 lb");
        assertEquals("1 lb 4 oz", say("20", Unit.OUNCE));
        assertEquals("1 lb", say("15.7", Unit.OUNCE));
    }

    @Test
    void smallOunceAmountsKeepHalves() {
        assertEquals("½ oz", say("0.5", Unit.OUNCE));
        assertEquals("1½ oz", say("0.1", Unit.POUND));
        assertEquals("< ½ oz", say("0.01", Unit.POUND));
    }

    @Test
    void metricDropsToTheSmallerUnitBelowOne() {
        assertEquals("750 g", say("0.75", Unit.KILOGRAM));
        assertEquals("1.5 kg", say("1.5", Unit.KILOGRAM));
        assertEquals("1.25 kg", say("1.25", Unit.KILOGRAM));
        assertEquals("1 kg", say("0.9996", Unit.KILOGRAM));
        assertEquals("454 g", say("453.592", Unit.GRAM));
        assertEquals("1.5 kg", say("1500", Unit.GRAM));
        assertEquals("400 ml", say("0.4", Unit.LITER));
        assertEquals("1.75 L", say("1.75", Unit.LITER));
    }

    @Test
    void tinyMetricAmountsKeepOneDecimal() {
        assertEquals("0.5 g", say("0.5", Unit.GRAM));
        assertEquals("< 0.1 g", say("0.04", Unit.GRAM));
    }

    @Test
    void cupsTablespoonsAndTeaspoonsUseTheMeasuringSet() {
        assertEquals("1½ cups", say("1.5", Unit.CUP));
        assertEquals("1 cup", say("1", Unit.CUP));
        assertEquals("¾ cup", say("0.75", Unit.CUP));
        assertEquals("⅓ cup", say("0.333", Unit.CUP));
        assertEquals("¾ cup 1 tbsp", say("0.8125", Unit.CUP));
        assertEquals("¼ cup 1 tbsp", say("5", Unit.TABLESPOON));
        assertEquals("1 tbsp 1½ tsp", say("1.5", Unit.TABLESPOON));
        assertEquals("1 tbsp", say("3", Unit.TEASPOON));
        assertEquals("¼ tsp", say("0.25", Unit.TEASPOON));
        assertEquals("⅛ tsp", say("0.125", Unit.TEASPOON));
        assertEquals("< ⅛ tsp", say("0.01", Unit.TEASPOON));
    }

    @Test
    void piecesUseFractions() {
        assertEquals("½ pc", say("0.5", Unit.PIECE));
        assertEquals("2⅓ pc", say("2.333", Unit.PIECE));
        assertEquals("12 pc", say("12.000", Unit.PIECE));
        assertEquals("3 pc", say("2.9", Unit.PIECE));
        assertEquals("< ¼ pc", say("0.1", Unit.PIECE));
    }

    @Test
    void zeroAndMissingValues() {
        assertEquals("0 lb", say("0", Unit.POUND));
        assertEquals("", KitchenAmounts.format(null, Unit.GRAM));
    }
}
