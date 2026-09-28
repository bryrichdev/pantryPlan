package edu.wgu.pantryprep.repository;

import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.PantryItem;
import edu.wgu.pantryprep.domain.StorageLocation;
import edu.wgu.pantryprep.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

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

    /**
     * Bulk delete for account removal. See AccountDeletionService for why the
     * order of these calls matters. Child rows go through ON DELETE CASCADE.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PantryItem p WHERE p.user = :user")
    int deleteAllOwnedBy(@Param("user") User user);
}
