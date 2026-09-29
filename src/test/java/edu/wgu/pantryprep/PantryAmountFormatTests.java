package edu.wgu.pantryprep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.web.ViewFormatter;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Pantry amounts show one decimal place; recipe amounts keep their precision. */
class PantryAmountFormatTests {

    private final ViewFormatter fmt = new ViewFormatter();

    @Test
    void pantryAmountsRoundToOneDecimalPlace() {
        assertEquals("453.6 g", fmt.pantryAmount(new BigDecimal("453.592"), Unit.GRAM));
        assertEquals("2 kg", fmt.pantryAmount(new BigDecimal("2.000"), Unit.KILOGRAM));
        assertEquals("1.5 lb", fmt.pantryAmount(new BigDecimal("1.500"), Unit.POUND));
        assertEquals("0.1 kg", fmt.pantryAmount(new BigDecimal("0.050"), Unit.KILOGRAM));
        assertEquals("0 g", fmt.pantryAmount(new BigDecimal("0.000"), Unit.GRAM));
    }

    @Test
    void aTraceStillOnTheShelfNeverReadsAsZero() {
        assertEquals("< 0.1 kg", fmt.pantryAmount(new BigDecimal("0.040"), Unit.KILOGRAM));
    }

    @Test
    void theFormPrefillIsAPlainNumberAStepOfPointOneAccepts() {
        assertEquals("453.6", fmt.pantryQuantity(new BigDecimal("453.592")));
        assertEquals("0", fmt.pantryQuantity(new BigDecimal("0.040")));
        assertEquals("12", fmt.pantryQuantity(new BigDecimal("12.000")));
    }

    @Test
    void recipeAmountsAreNotRounded() {
        assertEquals("0.125", fmt.quantity(new BigDecimal("0.125")));
    }
}
