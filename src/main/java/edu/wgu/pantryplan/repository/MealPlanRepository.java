package edu.wgu.pantryplan.repository;

import edu.wgu.pantryplan.domain.MealPlan;
import edu.wgu.pantryplan.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {

    Optional<MealPlan> findByIdAndUser(Long id, User user);

    long countByUser(User user);

    List<MealPlan> findAllByUserOrderByWeekStartDateDesc(User user);
}
