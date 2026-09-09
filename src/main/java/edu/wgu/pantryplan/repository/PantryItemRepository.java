package edu.wgu.pantryplan.repository;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.PantryItem;
import edu.wgu.pantryplan.domain.StorageLocation;
import edu.wgu.pantryplan.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PantryItemRepository extends JpaRepository<PantryItem, Long> {

    Optional<PantryItem> findByIdAndUser(Long id, User user);

    List<PantryItem> findAllByUserOrderByIngredientNameAsc(User user);

    List<PantryItem> findAllByUserAndIngredient(User user, Ingredient ingredient);

    List<PantryItem> findAllByUserAndExpiresOnLessThanEqualOrderByExpiresOnAsc(User user, LocalDate cutoff);

    boolean existsByIngredient(Ingredient ingredient);

    /**
     * Search by ingredient name and optional storage location. A null location
     * means "any location"; a blank term matches every ingredient name.
     */
    @Query("""
            SELECT p FROM PantryItem p
            JOIN p.ingredient i
            WHERE p.user = :user
              AND LOWER(i.name) LIKE LOWER(CONCAT('%', :term, '%'))
              AND (:location IS NULL OR p.location = :location)
            ORDER BY i.name ASC
            """)
    List<PantryItem> search(@Param("user") User user,
                            @Param("term") String term,
                            @Param("location") StorageLocation location);
}
