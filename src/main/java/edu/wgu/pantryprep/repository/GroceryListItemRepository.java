package edu.wgu.pantryprep.repository;

import edu.wgu.pantryprep.domain.GroceryList;
import edu.wgu.pantryprep.domain.GroceryListItem;
import edu.wgu.pantryprep.domain.Ingredient;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import edu.wgu.pantryprep.domain.PantryItem;

public interface GroceryListItemRepository extends JpaRepository<GroceryListItem, Long> {

    Optional<GroceryListItem> findByIdAndGroceryList(Long id, GroceryList groceryList);

    List<GroceryListItem> findAllByGroceryListOrderByCategoryAscIngredientNameAsc(GroceryList groceryList);
    List<GroceryListItem> findAllByPantryItem(PantryItem pantryItem);

    boolean existsByIngredient(Ingredient ingredient);
}
