package edu.wgu.pantryplan.service;

import edu.wgu.pantryplan.domain.Role;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.repository.MealPlanRepository;
import edu.wgu.pantryplan.repository.PantryItemRepository;
import edu.wgu.pantryplan.repository.RecipeRepository;
import edu.wgu.pantryplan.repository.UserRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    private final AccountDeletionService accountDeletionService;

    public AdminService(UserRepository userRepository,
                        RecipeRepository recipeRepository,
                        PantryItemRepository pantryItemRepository,
                        MealPlanRepository mealPlanRepository,
                        AccountDeletionService accountDeletionService) {
        this.userRepository = userRepository;
        this.recipeRepository = recipeRepository;
        this.pantryItemRepository = pantryItemRepository;
        this.mealPlanRepository = mealPlanRepository;
        this.accountDeletionService = accountDeletionService;
    }

    /**
     * Deletes the chosen accounts and everything they own.
     *
     * <p>Two kinds are always kept back: the admin's own account, which is
     * removed from the Account page instead, and any other administrator, so
     * one admin cannot lock another out. Each account is deleted on its own,
     * so one refusal does not stop the rest.
     */
    @Transactional
    public BulkDeleteResult deleteUsers(Collection<Long> ids, Long adminId) {
        BulkDeleteResult result = new BulkDeleteResult();
        if (ids == null) {
            return result;
        }
        Set<Long> unique = new LinkedHashSet<>(ids);
        unique.remove(null);
        for (Long id : unique) {
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                result.recordMissing();
            } else if (user.getId().equals(adminId) || user.getRole() == Role.ROLE_ADMIN) {
                result.recordBlocked(user.getDisplayName());
            } else {
                accountDeletionService.delete(user);
                result.recordDeleted();
            }
        }
        return result;
    }

    /**
     * Every account, alphabetical by display name, with a few counts to show
     * how much each one uses the app. Three small count queries per account is
     * fine at this scale; a large user base would want one grouped query.
     */
    @Transactional(readOnly = true)
    public List<AdminUserSummary> listUsers() {
        return userRepository.findAll(Sort.by(Sort.Order.asc("displayName").ignoreCase())).stream()
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
