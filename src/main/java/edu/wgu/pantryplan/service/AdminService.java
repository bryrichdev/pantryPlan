package edu.wgu.pantryplan.service;

import edu.wgu.pantryplan.domain.Role;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.repository.MealPlanRepository;
import edu.wgu.pantryplan.repository.PantryItemRepository;
import edu.wgu.pantryplan.repository.RecipeRepository;
import edu.wgu.pantryplan.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account overview for administrators. Access is enforced by the URL rules in
 * SecurityConfig, which only let ROLE_ADMIN reach the admin pages.
 */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;
    private final PantryItemRepository pantryItemRepository;
    private final MealPlanRepository mealPlanRepository;

    public AdminService(UserRepository userRepository,
                        RecipeRepository recipeRepository,
                        PantryItemRepository pantryItemRepository,
                        MealPlanRepository mealPlanRepository) {
        this.userRepository = userRepository;
        this.recipeRepository = recipeRepository;
        this.pantryItemRepository = pantryItemRepository;
        this.mealPlanRepository = mealPlanRepository;
    }

    /**
     * Every account, alphabetical by display name, with a few counts to show
     * how much each one uses the app. Three small count queries per account is
     * fine at this scale; a large user base would want one grouped query.
     */
    @Transactional(readOnly = true)
    public List<AdminUserSummary> listUsers() {
        Sort byName = Sort.by(Sort.Order.asc("displayName").ignoreCase());
        return userRepository.findAll(byName).stream()
                .map(this::summarize)
                .toList();
    }

    private AdminUserSummary summarize(User user) {
        return new AdminUserSummary(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole() == Role.ROLE_ADMIN,
                user.isEnabled(),
                user.getCreatedAt(),
                recipeRepository.countByUser(user),
                pantryItemRepository.countByUser(user),
                mealPlanRepository.countByUser(user));
    }
}
