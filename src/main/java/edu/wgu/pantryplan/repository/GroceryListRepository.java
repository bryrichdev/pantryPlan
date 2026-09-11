package edu.wgu.pantryplan.repository;

import edu.wgu.pantryplan.domain.GroceryList;
import edu.wgu.pantryplan.domain.MealPlan;
import edu.wgu.pantryplan.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

public interface GroceryListRepository extends JpaRepository<GroceryList, Long> {

    Optional<GroceryList> findByIdAndUser(Long id, User user);

    List<GroceryList> findAllByUserOrderByGeneratedAtDesc(User user);

    List<GroceryList> findAllByMealPlanOrderByGeneratedAtDesc(MealPlan mealPlan);

    Optional<GroceryList> findFirstByMealPlanOrderByGeneratedAtDesc(MealPlan mealPlan);

    /**
     * Bulk delete for account removal. See AccountDeletionService for why the
     * order of these calls matters. Child rows go through ON DELETE CASCADE.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GroceryList g WHERE g.user = :user")
    int deleteAllOwnedBy(@Param("user") User user);
}
