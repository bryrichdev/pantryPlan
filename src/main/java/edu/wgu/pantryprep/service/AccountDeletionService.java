package edu.wgu.pantryprep.service;

import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.repository.CookLogRepository;
import edu.wgu.pantryprep.repository.GroceryListRepository;
import edu.wgu.pantryprep.repository.MealPlanRepository;
import edu.wgu.pantryprep.repository.PantryItemRepository;
import edu.wgu.pantryprep.repository.RecipeRepository;
import edu.wgu.pantryprep.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes an account and everything it owns.
 *
 * <p>Every table cascades from users, but deleting the user row alone fails.
 * Ingredients and recipes are protected by ON DELETE RESTRICT, and Postgres
 * checks RESTRICT the moment a row goes, part-way through a cascade: removing
 * the user removes an ingredient while recipe lines still point at it, and the
 * whole delete is refused. Changing the keys to NO ACTION does not help either,
 * because a cascade's internal deletes are checked one at a time.
 *
 * <p>So the account is emptied from the outside in, each step removing the
 * last thing that points at the next: cook logs, grocery lists, meal plans,
 * pantry rows, recipes, and finally the user, which takes the now unreferenced
 * ingredients with it. Child rows (list items, plan entries, recipe lines and
 * tags) go through their own ON DELETE CASCADE at each step. The RESTRICT rules
 * stay exactly as they were for everyday deletes.
 *
 * <p>It all runs in one transaction, so a failure part-way leaves the account
 * untouched.
 */
@Service
public class AccountDeletionService {

    private final CookLogRepository cookLogRepository;
    private final GroceryListRepository groceryListRepository;
    private final MealPlanRepository mealPlanRepository;
    private final PantryItemRepository pantryItemRepository;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;

    public AccountDeletionService(CookLogRepository cookLogRepository,
                                  GroceryListRepository groceryListRepository,
                                  MealPlanRepository mealPlanRepository,
                                  PantryItemRepository pantryItemRepository,
                                  RecipeRepository recipeRepository,
                                  UserRepository userRepository) {
        this.cookLogRepository = cookLogRepository;
        this.groceryListRepository = groceryListRepository;
        this.mealPlanRepository = mealPlanRepository;
        this.pantryItemRepository = pantryItemRepository;
        this.recipeRepository = recipeRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void delete(User user) {
        cookLogRepository.deleteAllOwnedBy(user);
        groceryListRepository.deleteAllOwnedBy(user);
        mealPlanRepository.deleteAllOwnedBy(user);
        pantryItemRepository.deleteAllOwnedBy(user);
        recipeRepository.deleteAllOwnedBy(user);
        userRepository.deleteAccountRow(user.getId());
    }
}
