package edu.wgu.pantryplan.repository;

import edu.wgu.pantryplan.domain.CookLog;
import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.PlanEntry;
import edu.wgu.pantryplan.domain.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

public interface CookLogRepository extends JpaRepository<CookLog, Long> {

    List<CookLog> findAllByPlanEntryAndReversedFalse(PlanEntry planEntry);

    List<CookLog> findAllByPlanEntryOrderByCookedAtDesc(PlanEntry planEntry);

    List<CookLog> findAllByUserOrderByCookedAtDesc(User user);

    boolean existsByIngredient(Ingredient ingredient);

    /**
     * Bulk delete for account removal. See AccountDeletionService for why the
     * order of these calls matters. Child rows go through ON DELETE CASCADE.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CookLog c WHERE c.user = :user")
    int deleteAllOwnedBy(@Param("user") User user);
}
