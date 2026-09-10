package edu.wgu.pantryplan.repository;

import edu.wgu.pantryplan.domain.IngredientPreset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientPresetRepository extends JpaRepository<IngredientPreset, Long> {

    List<IngredientPreset> findAllByOrderByNameAsc();
}
