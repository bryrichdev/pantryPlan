package edu.wgu.pantryplan.repository;

import edu.wgu.pantryplan.domain.GroceryList;
import edu.wgu.pantryplan.domain.GroceryListItem;
import edu.wgu.pantryplan.domain.Ingredient;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroceryListItemRepository extends JpaRepository<GroceryListItem, Long> {

    Optional<GroceryListItem> findByIdAndGroceryList(Long id, GroceryList groceryList);

    List<GroceryListItem> findAllByGroceryListOrderByCategoryAscIngredientNameAsc(GroceryList groceryList);

    boolean existsByIngredient(Ingredient ingredient);
}
