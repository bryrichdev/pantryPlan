package edu.wgu.pantryplan.repository;

import edu.wgu.pantryplan.domain.GroceryList;
import edu.wgu.pantryplan.domain.MealPlan;
import edu.wgu.pantryplan.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroceryListRepository extends JpaRepository<GroceryList, Long> {

    Optional<GroceryList> findByIdAndUser(Long id, User user);

    List<GroceryList> findAllByUserOrderByGeneratedAtDesc(User user);

    List<GroceryList> findAllByMealPlanOrderByGeneratedAtDesc(MealPlan mealPlan);

    Optional<GroceryList> findFirstByMealPlanOrderByGeneratedAtDesc(MealPlan mealPlan);
}
