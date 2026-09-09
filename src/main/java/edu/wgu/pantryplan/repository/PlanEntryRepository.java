package edu.wgu.pantryplan.repository;

import edu.wgu.pantryplan.domain.MealPlan;
import edu.wgu.pantryplan.domain.PlanEntry;
import edu.wgu.pantryplan.domain.Recipe;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanEntryRepository extends JpaRepository<PlanEntry, Long> {

    Optional<PlanEntry> findByIdAndMealPlan(Long id, MealPlan mealPlan);

    List<PlanEntry> findAllByMealPlanOrderByPlanDateAscMealSlotAsc(MealPlan mealPlan);

    boolean existsByRecipe(Recipe recipe);
}
