package edu.wgu.pantryprep.repository;

import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.RecipeLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeLineRepository extends JpaRepository<RecipeLine, Long> {

    List<RecipeLine> findAllByRecipe(Recipe recipe);

    boolean existsByIngredient(Ingredient ingredient);
}
