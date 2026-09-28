package edu.wgu.pantryprep.service;

import edu.wgu.pantryprep.domain.Dimension;
import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.domain.UnitConversion;
import edu.wgu.pantryprep.repository.UnitConversionRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Converts a quantity from one unit to another.
 *
 * <p>The stored table holds one row per unit giving its size in that
 * dimension's canonical unit — grams for weight, millilitres for volume,
 * pieces for count. Ten rows describe every pairing within a dimension,
 * because any conversion routes through the canonical unit rather than being
 * stored directly. Adding a unit later is one insert, not N.
 *
 * <p>Crossing between weight and volume is the case the table cannot answer,
 * since the ratio depends on what is being measured: a cup of flour and a cup
 * of honey weigh very different amounts. Those conversions use the
 * ingredient's own {@code gramsPerCup}, and fail cleanly when it is not set.
 */
@Service
public class UnitConversionService {

    /**
     * Twelve significant digits, which is well beyond the three decimal places
     * the database stores. Division has to be told how to round or BigDecimal
     * throws on any non-terminating result, so this is required rather than
     * decorative.
     */
    private static final MathContext PRECISION = new MathContext(12, RoundingMode.HALF_UP);

    private final UnitConversionRepository unitConversionRepository;

    /**
     * Reference data, loaded once. It only changes by migration, so re-reading
     * it per conversion would be ten rows fetched for every line of every
     * grocery list.
     */
    private volatile Map<Unit, BigDecimal> canonicalFactors;

    public UnitConversionService(UnitConversionRepository unitConversionRepository) {
        this.unitConversionRepository = unitConversionRepository;
    }

    /**
     * Converts a quantity, or returns empty when no conversion exists.
     *
     * <p>Callers that can carry on without an answer — the grocery list, which
     * flags the line for manual checking — should use this. Callers that cannot
     * should use {@link #requireConvert}.
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> convert(BigDecimal quantity, Unit from, Unit to, Ingredient ingredient) {
        if (quantity == null || from == null || to == null) {
            return Optional.empty();
        }
        if (from == to) {
            return Optional.of(quantity);
        }
        if (from.sameDimensionAs(to)) {
            return Optional.of(convertWithinDimension(quantity, from, to));
        }
        if (from.getDimension() == Dimension.COUNT || to.getDimension() == Dimension.COUNT) {
            return Optional.empty();
        }
        if (ingredient == null || !ingredient.hasVolumeWeightRatio()) {
            return Optional.empty();
        }
        return Optional.of(convertAcrossDimensions(quantity, from, to, ingredient.getGramsPerCup()));
    }

    /**
     * Converts a quantity, throwing when no conversion exists.
     *
     * @throws ConversionNotPossibleException when the units cannot be related
     */
    @Transactional(readOnly = true)
    public BigDecimal requireConvert(BigDecimal quantity, Unit from, Unit to, Ingredient ingredient) {
        return convert(quantity, from, to, ingredient)
                .orElseThrow(() -> new ConversionNotPossibleException(from, to, explainFailure(from, to, ingredient)));
    }

    /**
     * Whether a conversion between these units is possible for this ingredient.
     */
    @Transactional(readOnly = true)
    public boolean canConvert(Unit from, Unit to, Ingredient ingredient) {
        return convert(BigDecimal.ONE, from, to, ingredient).isPresent();
    }

    /**
     * A sentence explaining why a conversion is unavailable, suitable for
     * showing to a cook.
     */
    public String explainFailure(Unit from, Unit to, Ingredient ingredient) {
        if (from == null || to == null) {
            return "the units are missing";
        }
        if (from.getDimension() == Dimension.COUNT || to.getDimension() == Dimension.COUNT) {
            return "counted items cannot be converted to or from a measured amount";
        }
        String name = ingredient == null ? "this ingredient" : ingredient.getName();
        return "converting between volume and weight needs a weight per cup for " + name;
    }

    /**
     * Expresses a quantity in its dimension's canonical unit, which is what
     * makes two amounts in different units directly comparable.
     */
    @Transactional(readOnly = true)
    public BigDecimal toCanonical(BigDecimal quantity, Unit unit) {
        return quantity.multiply(factorFor(unit), PRECISION);
    }

    /**
     * Same-dimension conversion: scale up to the canonical unit, then down to
     * the target. Kept as one multiply and one divide so rounding happens once.
     */
    private BigDecimal convertWithinDimension(BigDecimal quantity, Unit from, Unit to) {
        return quantity.multiply(factorFor(from)).divide(factorFor(to), PRECISION);
    }

    /**
     * Volume to weight or the reverse, routed through cups because that is the
     * unit the ingredient's density is expressed in.
     *
     * <p>Volume to weight is {@code qty x mlPerFrom x gramsPerCup / (mlPerCup x gramsPerTo)}.
     * Weight to volume inverts the density and the cup factor. Both are written
     * as a single division so there is one rounding step rather than four.
     */
    private BigDecimal convertAcrossDimensions(BigDecimal quantity, Unit from, Unit to,
                                               BigDecimal gramsPerCup) {
        BigDecimal mlPerCup = factorFor(Unit.CUP);

        if (from.getDimension() == Dimension.VOLUME) {
            BigDecimal numerator = quantity.multiply(factorFor(from)).multiply(gramsPerCup);
            BigDecimal denominator = mlPerCup.multiply(factorFor(to));
            return numerator.divide(denominator, PRECISION);
        }

        BigDecimal numerator = quantity.multiply(factorFor(from)).multiply(mlPerCup);
        BigDecimal denominator = gramsPerCup.multiply(factorFor(to));
        return numerator.divide(denominator, PRECISION);
    }

    private BigDecimal factorFor(Unit unit) {
        BigDecimal factor = factors().get(unit);
        if (factor == null) {
            throw new IllegalStateException("No conversion row seeded for " + unit);
        }
        return factor;
    }

    private Map<Unit, BigDecimal> factors() {
        Map<Unit, BigDecimal> loaded = canonicalFactors;
        if (loaded == null) {
            synchronized (this) {
                loaded = canonicalFactors;
                if (loaded == null) {
                    loaded = loadFactors();
                    canonicalFactors = loaded;
                }
            }
        }
        return loaded;
    }

    private Map<Unit, BigDecimal> loadFactors() {
        Map<Unit, BigDecimal> map = new EnumMap<>(Unit.class);
        for (UnitConversion row : unitConversionRepository.findAll()) {
            if (row.getToUnit() == row.getFromUnit().canonical()) {
                map.put(row.getFromUnit(), row.getFactor());
            }
        }
        return map;
    }
}
