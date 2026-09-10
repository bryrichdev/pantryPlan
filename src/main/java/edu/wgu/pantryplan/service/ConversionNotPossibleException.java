package edu.wgu.pantryplan.service;

import edu.wgu.pantryplan.domain.Unit;

/**
 * Raised when a conversion is asked for that cannot be computed.
 *
 * <p>Two cases reach here. Counted things do not convert to or from measured
 * ones — there is no general answer for how many grams an egg weighs. And a
 * volume-to-weight conversion needs the ingredient's own density, which is
 * optional and often absent.
 */
public class ConversionNotPossibleException extends RuntimeException {

    private final Unit fromUnit;
    private final Unit toUnit;

    public ConversionNotPossibleException(Unit fromUnit, Unit toUnit, String message) {
        super(message);
        this.fromUnit = fromUnit;
        this.toUnit = toUnit;
    }

    public Unit getFromUnit() {
        return fromUnit;
    }

    public Unit getToUnit() {
        return toUnit;
    }
}
