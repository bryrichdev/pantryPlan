package edu.wgu.pantryprep.repository;

import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.domain.UnitConversion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitConversionRepository extends JpaRepository<UnitConversion, Long> {

    Optional<UnitConversion> findByFromUnitAndToUnit(Unit fromUnit, Unit toUnit);
}
