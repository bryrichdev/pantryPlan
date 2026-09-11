package edu.wgu.pantryplan.repository;

import edu.wgu.pantryplan.domain.CookLog;
import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.PlanEntry;
import edu.wgu.pantryplan.domain.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CookLogRepository extends JpaRepository<CookLog, Long> {

    List<CookLog> findAllByPlanEntryAndReversedFalse(PlanEntry planEntry);

    List<CookLog> findAllByPlanEntryOrderByCookedAtDesc(PlanEntry planEntry);

    List<CookLog> findAllByUserOrderByCookedAtDesc(User user);

    boolean existsByIngredient(Ingredient ingredient);
}
