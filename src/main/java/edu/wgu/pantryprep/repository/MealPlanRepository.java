package edu.wgu.pantryprep.repository;

import edu.wgu.pantryprep.domain.MealPlan;
import edu.wgu.pantryprep.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {

    Optional<MealPlan> findByIdAndUser(Long id, User user);

    long countByUser(User user);

    List<MealPlan> findAllByUserOrderByWeekStartDateDesc(User user);

    /**
     * Bulk delete for account removal. See AccountDeletionService for why the
     * order of these calls matters. Child rows go through ON DELETE CASCADE.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM MealPlan m WHERE m.user = :user")
    int deleteAllOwnedBy(@Param("user") User user);
}
