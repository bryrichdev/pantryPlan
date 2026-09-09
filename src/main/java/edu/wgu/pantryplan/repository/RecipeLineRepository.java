package edu.wgu.pantryplan.repository;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.Recipe;
import edu.wgu.pantryplan.domain.RecipeLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeLineRepository extends JpaRepository<RecipeLine, Long> {

    List<RecipeLine> findAllByRecipe(Recipe recipe);

    boolean existsByIngredient(Ingredient ingredient);
}
