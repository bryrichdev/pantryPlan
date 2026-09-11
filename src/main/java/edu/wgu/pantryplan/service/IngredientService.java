package edu.wgu.pantryplan.service;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.IngredientPreset;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.repository.CookLogRepository;
import edu.wgu.pantryplan.repository.GroceryListItemRepository;
import edu.wgu.pantryplan.repository.IngredientPresetRepository;
import edu.wgu.pantryplan.repository.IngredientRepository;
import edu.wgu.pantryplan.repository.PantryItemRepository;
import edu.wgu.pantryplan.repository.RecipeLineRepository;
import edu.wgu.pantryplan.web.form.IngredientForm;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingredient catalogue for a single cook.
 *
 * <p>Every method takes the owning {@link User} and every query filters on it,
 * so one account can never read or change another account's records.
 */
@Service
public class IngredientService {

    private final IngredientRepository ingredientRepository;
    private final RecipeLineRepository recipeLineRepository;
    private final PantryItemRepository pantryItemRepository;
    private final GroceryListItemRepository groceryListItemRepository;
    private final IngredientPresetRepository ingredientPresetRepository;
    private final CookLogRepository cookLogRepository;

    public IngredientService(IngredientRepository ingredientRepository,
                             RecipeLineRepository recipeLineRepository,
                             PantryItemRepository pantryItemRepository,
                             GroceryListItemRepository groceryListItemRepository,
                             IngredientPresetRepository ingredientPresetRepository,
                             CookLogRepository cookLogRepository) {
        this.ingredientRepository = ingredientRepository;
        this.recipeLineRepository = recipeLineRepository;
        this.pantryItemRepository = pantryItemRepository;
        this.groceryListItemRepository = groceryListItemRepository;
        this.ingredientPresetRepository = ingredientPresetRepository;
        this.cookLogRepository = cookLogRepository;
    }

    @Transactional(readOnly = true)
    public List<Ingredient> findAll(User user) {
        return ingredientRepository.findAllByUserOrderByNameAsc(user);
    }

    /**
     * Name search. A blank term returns the full catalogue rather than nothing,
     * so clearing the search box restores the list.
     */
    @Transactional(readOnly = true)
    public List<Ingredient> search(User user, String term) {
        if (term == null || term.isBlank()) {
            return findAll(user);
        }
        return ingredientRepository
                .findAllByUserAndNameContainingIgnoreCaseOrderByNameAsc(user, term.trim());
    }


    /**
     * Copies the shared preset catalogue into this account's ingredient list.
     *
     * <p>Presets whose name the account already uses are skipped, compared
     * without regard to case, so running this twice adds nothing the second
     * time and an existing "Butter" is never duplicated or overwritten. Each
     * copy is an ordinary ingredient from then on, free to be edited or deleted.
     *
     * @return how many were added
     */
    @Transactional
    public int importPresets(User user) {
        Set<String> alreadyHave = new HashSet<>();
        for (Ingredient existing : ingredientRepository.findAllByUserOrderByNameAsc(user)) {
            alreadyHave.add(existing.getName().toLowerCase());
        }

        int added = 0;
        for (IngredientPreset preset : ingredientPresetRepository.findAllByOrderByNameAsc()) {
            if (alreadyHave.contains(preset.getName().toLowerCase())) {
                continue;
            }
            Ingredient ingredient = new Ingredient(user, preset.getName(), preset.getCategory());
            ingredient.setStockUnit(preset.getStockUnit());
            ingredient.setDefaultLocation(preset.getDefaultLocation());
            ingredient.setDefaultQuantity(preset.getDefaultQuantity());
            ingredient.setGramsPerCup(preset.getGramsPerCup());
            ingredientRepository.save(ingredient);
            added++;
        }
        return added;
    }

    /**
     * How many presets this account does not have yet, so the interface can
     * label the action honestly and hide it once there is nothing to add.
     */
    @Transactional(readOnly = true)
    public int countMissingPresets(User user) {
        Set<String> alreadyHave = new HashSet<>();
        for (Ingredient existing : ingredientRepository.findAllByUserOrderByNameAsc(user)) {
            alreadyHave.add(existing.getName().toLowerCase());
        }
        int missing = 0;
        for (IngredientPreset preset : ingredientPresetRepository.findAllByOrderByNameAsc()) {
            if (!alreadyHave.contains(preset.getName().toLowerCase())) {
                missing++;
            }
        }
        return missing;
    }

    @Transactional(readOnly = true)
    public Ingredient requireOwned(Long id, User user) {
        return ingredientRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new NoSuchElementException("No ingredient " + id + " for this account"));
    }

    @Transactional
    public Ingredient create(IngredientForm form, User user) {
        Ingredient ingredient = new Ingredient(user, form.getName().trim(), form.getCategory());
        applyForm(ingredient, form);
        return ingredientRepository.save(ingredient);
    }

    @Transactional
    public Ingredient update(Long id, IngredientForm form, User user) {
        Ingredient ingredient = requireOwned(id, user);
        ingredient.setName(form.getName().trim());
        ingredient.setCategory(form.getCategory());
        applyForm(ingredient, form);
        return ingredientRepository.save(ingredient);
    }

    private void applyForm(Ingredient ingredient, IngredientForm form) {
        ingredient.setStockUnit(form.getStockUnit());
        ingredient.setDefaultLocation(form.getDefaultLocation());
        ingredient.setDefaultQuantity(form.getDefaultQuantity());
        ingredient.setGramsPerCup(form.getGramsPerCup());
    }

    /**
     * Deletes an ingredient only when nothing references it. The schema enforces
     * this too, but checking here produces a message a cook can act on rather
     * than a constraint violation.
     */
    @Transactional
    public void delete(Long id, User user) {
        Ingredient ingredient = requireOwned(id, user);
        String reason = describeReferences(ingredient);
        if (reason != null) {
            throw new IngredientInUseException(ingredient.getName(), reason);
        }
        ingredientRepository.delete(ingredient);
    }


    /**
     * Deletes several ingredients, keeping back any that are still referenced.
     *
     * <p>Each is checked and removed on its own, so one blocked ingredient does
     * not stop the rest. Ids that do not belong to this account are counted as
     * missing rather than raising, since a stale page can easily submit one.
     */
    @Transactional
    public BulkDeleteResult deleteAll(Collection<Long> ids, User user) {
        BulkDeleteResult result = new BulkDeleteResult();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        for (Long id : ids) {
            Ingredient ingredient = ingredientRepository.findByIdAndUser(id, user).orElse(null);
            if (ingredient == null) {
                result.recordMissing();
                continue;
            }
            if (describeReferences(ingredient) != null) {
                result.recordBlocked(ingredient.getName());
                continue;
            }
            ingredientRepository.delete(ingredient);
            result.recordDeleted();
        }
        return result;
    }

    /**
     * @return a human-readable reason the ingredient is still in use,
     *         or {@code null} when it is safe to delete
     */
    @Transactional(readOnly = true)
    public String describeReferences(Ingredient ingredient) {
        if (recipeLineRepository.existsByIngredient(ingredient)) {
            return "one or more recipes still list this ingredient";
        }
        if (pantryItemRepository.existsByIngredient(ingredient)) {
            return "this ingredient is still on a pantry shelf";
        }
        if (groceryListItemRepository.existsByIngredient(ingredient)) {
            return "this ingredient appears on a saved grocery list";
        }
        /* Cook logs keep the ingredient so a cooked meal can be undone and its
           stock put back. The database enforces this with ON DELETE RESTRICT;
           checking here turns a failed delete into an explanation. */
        if (cookLogRepository.existsByIngredient(ingredient)) {
            return "a cooked meal in a meal plan still records using it";
        }
        return null;
    }

    @Transactional(readOnly = true)
    public boolean isDeletable(Ingredient ingredient) {
        return describeReferences(ingredient) == null;
    }

    /**
     * @return true when the name is already used by a different ingredient
     *         on this account
     */
    @Transactional(readOnly = true)
    public boolean nameCollides(User user, String name, Long allowedId) {
        if (name == null || name.isBlank()) {
            return false;
        }
        Optional<Ingredient> existing =
                ingredientRepository.findByUserAndNameIgnoreCase(user, name.trim());
        return existing.isPresent() && !existing.get().getId().equals(allowedId);
    }
}
