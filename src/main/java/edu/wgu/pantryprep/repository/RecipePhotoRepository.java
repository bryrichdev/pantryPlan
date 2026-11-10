package edu.wgu.pantryprep.repository;

import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.RecipePhoto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipePhotoRepository extends JpaRepository<RecipePhoto, Long> {

    List<RecipePhoto> findByRecipeOrderByPositionAscIdAsc(Recipe recipe);

    Optional<RecipePhoto> findByIdAndRecipe(Long id, Recipe recipe);

    long countByRecipe(Recipe recipe);
}
