package edu.wgu.pantryplan.repository;

import edu.wgu.pantryplan.domain.Recipe;
import edu.wgu.pantryplan.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    Optional<Recipe> findByIdAndUser(Long id, User user);

    long countByUser(User user);

    List<Recipe> findAllByUserOrderByNameAsc(User user);

    List<Recipe> findAllByUserOrderByTimesCookedDescNameAsc(User user);

    /**
     * Multi-criteria search across recipe name, ingredient name, and tag.
     * Blank arguments are treated as "match anything", so callers pass ""
     * rather than null and every parameter keeps a resolvable type.
     */
    @Query("""
            SELECT DISTINCT r FROM Recipe r
            LEFT JOIN r.lines l
            LEFT JOIN l.ingredient i
            LEFT JOIN r.tags t
            WHERE r.user = :user
              AND LOWER(r.name) LIKE LOWER(CONCAT('%', :name, '%'))
              AND (:ingredient = '' OR LOWER(i.name) LIKE LOWER(CONCAT('%', :ingredient, '%')))
              AND (:tag = '' OR LOWER(t) LIKE LOWER(CONCAT('%', :tag, '%')))
            ORDER BY r.name ASC
            """)
    List<Recipe> search(@Param("user") User user,
                        @Param("name") String name,
                        @Param("ingredient") String ingredient,
                        @Param("tag") String tag);

    /**
     * Bulk delete for account removal. See AccountDeletionService for why the
     * order of these calls matters. Child rows go through ON DELETE CASCADE.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Recipe r WHERE r.user = :user")
    int deleteAllOwnedBy(@Param("user") User user);
}
