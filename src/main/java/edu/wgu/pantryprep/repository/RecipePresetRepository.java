package edu.wgu.pantryprep.repository;

import edu.wgu.pantryprep.domain.RecipePreset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipePresetRepository extends JpaRepository<RecipePreset, Long> {

    List<RecipePreset> findAllByOrderByNameAsc();
}
