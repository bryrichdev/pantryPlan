package edu.wgu.pantryprep.repository;

import edu.wgu.pantryprep.domain.MealPlan;
import edu.wgu.pantryprep.domain.PlanEntry;
import edu.wgu.pantryprep.domain.Recipe;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanEntryRepository extends JpaRepository<PlanEntry, Long> {

    Optional<PlanEntry> findByIdAndMealPlan(Long id, MealPlan mealPlan);

    List<PlanEntry> findAllByMealPlanOrderByPlanDateAscMealSlotAsc(MealPlan mealPlan);

    boolean existsByRecipe(Recipe recipe);

    long countByRecipeAndCookedTrue(Recipe recipe);

    Optional<PlanEntry> findFirstByRecipeAndCookedTrueOrderByCookedAtDesc(Recipe recipe);
}
