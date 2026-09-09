package edu.wgu.pantryplan.repository;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    Optional<Ingredient> findByIdAndUser(Long id, User user);

    List<Ingredient> findAllByUserOrderByNameAsc(User user);

    List<Ingredient> findAllByUserAndNameContainingIgnoreCaseOrderByNameAsc(User user, String name);

    boolean existsByUserAndNameIgnoreCase(User user, String name);
}
