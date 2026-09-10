package edu.wgu.pantryplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.IngredientCategory;
import edu.wgu.pantryplan.domain.Unit;
import edu.wgu.pantryplan.service.ConversionNotPossibleException;
import edu.wgu.pantryplan.service.UnitConversionService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * The conversion engine is the piece the grocery list depends on for a correct
 * answer, so it gets the closest scrutiny. Nothing here touches the web layer.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class UnitConversionServiceTests {

    /** Decimal conversion factors do not land on exact values, so compare within a tolerance. */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.000001");

    @Autowired
    private UnitConversionService conversionService;

    private void assertClose(String expected, Optional<BigDecimal> actual, String message) {
        assertTrue(actual.isPresent(), message + " (no conversion returned)");
        BigDecimal difference = actual.get().subtract(new BigDecimal(expected)).abs();
        assertTrue(difference.compareTo(TOLERANCE) <= 0,
                message + " — expected about " + expected + " but got " + actual.get());
    }

    /** An ingredient measured both ways: flour at 120 g per cup. */
    private Ingredient flour() {
        Ingredient ingredient = new Ingredient(null, "Flour", IngredientCategory.PANTRY_STAPLE);
        ingredient.setGramsPerCup(new BigDecimal("120.000"));
        return ingredient;
    }

    /** An ingredient with no density recorded. */
    private Ingredient mystery() {
        return new Ingredient(null, "Mystery powder", IngredientCategory.OTHER);
    }

    @Test
    void returnsTheSameQuantityForTheSameUnit() {
        assertClose("2.5", conversionService.convert(new BigDecimal("2.5"), Unit.CUP, Unit.CUP, null),
                "converting a unit to itself changes nothing");
    }

    @Test
    void convertsWithinWeight() {
        assertClose("1000", conversionService.convert(BigDecimal.ONE, Unit.KILOGRAM, Unit.GRAM, null),
                "1 kg in grams");
        assertClose("453.59237", conversionService.convert(BigDecimal.ONE, Unit.POUND, Unit.GRAM, null),
                "1 lb in grams");
        assertClose("16", conversionService.convert(BigDecimal.ONE, Unit.POUND, Unit.OUNCE, null),
                "1 lb in ounces");
        assertClose("0.5", conversionService.convert(new BigDecimal("500"), Unit.GRAM, Unit.KILOGRAM, null),
                "500 g in kilograms");
    }

    @Test
    void convertsWithinVolume() {
        assertClose("236.5882365", conversionService.convert(BigDecimal.ONE, Unit.CUP, Unit.MILLILITER, null),
                "1 cup in millilitres");
        assertClose("1000", conversionService.convert(BigDecimal.ONE, Unit.LITER, Unit.MILLILITER, null),
                "1 litre in millilitres");
        assertClose("3", conversionService.convert(BigDecimal.ONE, Unit.TABLESPOON, Unit.TEASPOON, null),
                "1 tablespoon in teaspoons");
        assertClose("16", conversionService.convert(BigDecimal.ONE, Unit.CUP, Unit.TABLESPOON, null),
                "1 cup in tablespoons");
    }

    @Test
    void convertsVolumeToWeightUsingTheIngredientDensity() {
        assertClose("240", conversionService.convert(new BigDecimal("2"), Unit.CUP, Unit.GRAM, flour()),
                "2 cups of flour at 120 g per cup");
        assertClose("120", conversionService.convert(BigDecimal.ONE, Unit.CUP, Unit.GRAM, flour()),
                "1 cup of flour");
        assertClose("7.5", conversionService.convert(BigDecimal.ONE, Unit.TABLESPOON, Unit.GRAM, flour()),
                "1 tablespoon of flour is a sixteenth of a cup");
    }

    @Test
    void convertsWeightToVolumeUsingTheIngredientDensity() {
        assertClose("2", conversionService.convert(new BigDecimal("240"), Unit.GRAM, Unit.CUP, flour()),
                "240 g of flour back into cups");
        assertClose("3.779936417", conversionService.convert(BigDecimal.ONE, Unit.POUND, Unit.CUP, flour()),
                "1 lb of flour in cups");
    }

    @Test
    void roundTripsBackToTheStartingQuantity() {
        Optional<BigDecimal> grams =
                conversionService.convert(new BigDecimal("1.75"), Unit.CUP, Unit.GRAM, flour());
        assertTrue(grams.isPresent());

        assertClose("1.75", conversionService.convert(grams.get(), Unit.GRAM, Unit.CUP, flour()),
                "converting out and back should land where it started");
    }

    @Test
    void refusesVolumeToWeightWithoutADensity() {
        assertTrue(conversionService.convert(BigDecimal.ONE, Unit.CUP, Unit.GRAM, mystery()).isEmpty(),
                "no weight per cup means no answer");
        assertTrue(conversionService.convert(BigDecimal.ONE, Unit.CUP, Unit.GRAM, null).isEmpty(),
                "no ingredient at all means no answer");
        assertFalse(conversionService.canConvert(Unit.CUP, Unit.GRAM, mystery()));
    }

    @Test
    void refusesToConvertCountedItems() {
        assertTrue(conversionService.convert(BigDecimal.ONE, Unit.PIECE, Unit.GRAM, flour()).isEmpty(),
                "pieces do not convert to a weight");
        assertTrue(conversionService.convert(BigDecimal.ONE, Unit.CUP, Unit.PIECE, flour()).isEmpty(),
                "a volume does not convert to a count");
        assertClose("3", conversionService.convert(new BigDecimal("3"), Unit.PIECE, Unit.PIECE, null),
                "pieces to pieces is still fine");
    }

    @Test
    void requireConvertThrowsWithAReadableReason() {
        ConversionNotPossibleException thrown = assertThrows(ConversionNotPossibleException.class,
                () -> conversionService.requireConvert(BigDecimal.ONE, Unit.CUP, Unit.GRAM, mystery()));

        assertEquals(Unit.CUP, thrown.getFromUnit());
        assertEquals(Unit.GRAM, thrown.getToUnit());
        assertTrue(thrown.getMessage().contains("weight per cup"));
        assertTrue(thrown.getMessage().contains("Mystery powder"),
                "the message should name the ingredient that needs the density");
    }

    @Test
    void explainsCountedFailuresDifferentlyFromDensityFailures() {
        assertTrue(conversionService.explainFailure(Unit.PIECE, Unit.GRAM, flour()).contains("counted"));
        assertTrue(conversionService.explainFailure(Unit.CUP, Unit.GRAM, mystery()).contains("weight per cup"));
    }

    @Test
    void normalisesToTheCanonicalUnitOfEachDimension() {
        assertEquals(0, new BigDecimal("1000").compareTo(
                conversionService.toCanonical(BigDecimal.ONE, Unit.KILOGRAM)),
                "weight normalises to grams");
        assertEquals(0, new BigDecimal("1000").compareTo(
                conversionService.toCanonical(BigDecimal.ONE, Unit.LITER)),
                "volume normalises to millilitres");
    }

    @Test
    void handlesFractionalAndLargeQuantities() {
        assertClose("0.0625", conversionService.convert(BigDecimal.ONE, Unit.TABLESPOON, Unit.CUP, null),
                "a tablespoon is a sixteenth of a cup");
        assertClose("45359.237", conversionService.convert(new BigDecimal("100"), Unit.POUND, Unit.GRAM, null),
                "large weights stay exact");
    }

    @Test
    void treatsMissingArgumentsAsUnconvertible() {
        assertTrue(conversionService.convert(null, Unit.CUP, Unit.GRAM, flour()).isEmpty());
        assertTrue(conversionService.convert(BigDecimal.ONE, null, Unit.GRAM, flour()).isEmpty());
        assertTrue(conversionService.convert(BigDecimal.ONE, Unit.CUP, null, flour()).isEmpty());
    }
}
