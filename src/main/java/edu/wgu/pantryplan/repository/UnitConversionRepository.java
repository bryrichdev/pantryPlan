package edu.wgu.pantryplan.repository;

import edu.wgu.pantryplan.domain.Unit;
import edu.wgu.pantryplan.domain.UnitConversion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitConversionRepository extends JpaRepository<UnitConversion, Long> {

    Optional<UnitConversion> findByFromUnitAndToUnit(Unit fromUnit, Unit toUnit);
}
