package edu.wgu.pantryplan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "unit_conversions")
public class UnitConversion extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "from_unit", nullable = false, length = 20)
    private Unit fromUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_unit", nullable = false, length = 20)
    private Unit toUnit;

    @Column(name = "factor", nullable = false, precision = 18, scale = 9)
    private BigDecimal factor;

    protected UnitConversion() {
    }

    public UnitConversion(Unit fromUnit, Unit toUnit, BigDecimal factor) {
        this.fromUnit = fromUnit;
        this.toUnit = toUnit;
        this.factor = factor;
    }

    public BigDecimal apply(BigDecimal quantity) {
        return quantity.multiply(factor);
    }

    public Unit getFromUnit() {
        return fromUnit;
    }

    public Unit getToUnit() {
        return toUnit;
    }

    public BigDecimal getFactor() {
        return factor;
    }
}
