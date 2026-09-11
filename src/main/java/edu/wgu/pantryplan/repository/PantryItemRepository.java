package edu.wgu.pantryplan.repository;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.PantryItem;
import edu.wgu.pantryplan.domain.StorageLocation;
import edu.wgu.pantryplan.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Four separate finders rather than one query with optional parameters. A
 * nullable enum parameter forces the driver to guess a type it cannot infer,
 * so the service picks the finder that matches the filters in play instead.
 */
public interface PantryItemRepository extends JpaRepository<PantryItem, Long> {

    Optional<PantryItem> findByIdAndUser(Long id, User user);

    long countByUser(User user);

    List<PantryItem> findAllByUserOrderByIngredientNameAsc(User user);

    List<PantryItem> findAllByUserAndIngredientNameContainingIgnoreCaseOrderByIngredientNameAsc(
            User user, String term);

    List<PantryItem> findAllByUserAndLocationOrderByIngredientNameAsc(
            User user, StorageLocation location);

    List<PantryItem> findAllByUserAndLocationAndIngredientNameContainingIgnoreCaseOrderByIngredientNameAsc(
            User user, StorageLocation location, String term);

    List<PantryItem> findAllByUserAndIngredient(User user, Ingredient ingredient);

    List<PantryItem> findAllByUserAndExpiresOnLessThanEqualOrderByExpiresOnAsc(
            User user, LocalDate cutoff);

    boolean existsByIngredient(Ingredient ingredient);
}
