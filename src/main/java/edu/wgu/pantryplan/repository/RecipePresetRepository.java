package edu.wgu.pantryplan.repository;

import edu.wgu.pantryplan.domain.RecipePreset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipePresetRepository extends JpaRepository<RecipePreset, Long> {

    List<RecipePreset> findAllByOrderByNameAsc();
}
