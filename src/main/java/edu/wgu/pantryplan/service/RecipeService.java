package edu.wgu.pantryplan.service;

import edu.wgu.pantryplan.domain.Recipe;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.repository.PlanEntryRepository;
import edu.wgu.pantryplan.repository.RecipeRepository;
import edu.wgu.pantryplan.web.form.RecipeForm;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recipe management for a single cook.
 *
 * <p>Read methods deliberately touch the lazy collections before returning.
 * {@code spring.jpa.open-in-view} is off, so entities are detached by the time
 * Thymeleaf renders them, and anything the views read has to be loaded while
 * the transaction is still open.
 */
@Service
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final PlanEntryRepository planEntryRepository;

    public RecipeService(RecipeRepository recipeRepository,
                         PlanEntryRepository planEntryRepository) {
        this.recipeRepository = recipeRepository;
        this.planEntryRepository = planEntryRepository;
    }

    @Transactional(readOnly = true)
    public List<Recipe> findAll(User user) {
        return withTags(recipeRepository.findAllByUserOrderByNameAsc(user));
    }

    /**
     * Multi-criteria search. Any blank criterion is ignored rather than treated
     * as "match nothing", so a search on tag alone returns every recipe carrying
     * that tag regardless of name.
     */
    @Transactional(readOnly = true)
    public List<Recipe> search(User user, String name, String ingredient, String tag) {
        return withTags(recipeRepository.search(
                user, blankIfNull(name), blankIfNull(ingredient), blankIfNull(tag)));
    }

    @Transactional(readOnly = true)
    public Recipe requireOwned(Long id, User user) {
        Recipe recipe = recipeRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new NoSuchElementException("No recipe " + id + " for this account"));
        recipe.getTags().size();
        recipe.getLines().forEach(line -> line.getIngredient().getName());
        return recipe;
    }

    /**
     * Forces the tag collection to load. Calling size() on a lazy collection is
     * what triggers Hibernate to fetch it.
     */
    private List<Recipe> withTags(List<Recipe> recipes) {
        recipes.forEach(recipe -> recipe.getTags().size());
        return recipes;
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value.trim();
    }

    @Transactional
    public Recipe create(RecipeForm form, User user) {
        Recipe recipe = new Recipe(user, form.getName().trim(), form.getServings());
        applyForm(recipe, form);
        return recipeRepository.save(recipe);
    }

    @Transactional
    public Recipe update(Long id, RecipeForm form, User user) {
        Recipe recipe = requireOwned(id, user);
        recipe.setName(form.getName().trim());
        recipe.setServings(form.getServings());
        applyForm(recipe, form);
        return recipeRepository.save(recipe);
    }

    private void applyForm(Recipe recipe, RecipeForm form) {
        recipe.setDescription(emptyToNull(form.getDescription()));
        recipe.setInstructions(emptyToNull(form.getInstructions()));
        recipe.setPrepMinutes(form.getPrepMinutes() == null ? 0 : form.getPrepMinutes());
        recipe.setCookMinutes(form.getCookMinutes() == null ? 0 : form.getCookMinutes());
        recipe.setTags(form.parsedTags());
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional
    public void delete(Long id, User user) {
        Recipe recipe = requireOwned(id, user);
        String reason = describeReferences(recipe);
        if (reason != null) {
            throw new RecipeInUseException(recipe.getName(), reason);
        }
        recipeRepository.delete(recipe);
    }

    /**
     * @return why the recipe cannot be deleted, or {@code null} when it is safe
     */
    @Transactional(readOnly = true)
    public String describeReferences(Recipe recipe) {
        if (planEntryRepository.existsByRecipe(recipe)) {
            return "a meal plan still schedules this recipe";
        }
        return null;
    }

    @Transactional(readOnly = true)
    public boolean nameCollides(User user, String name, Long allowedId) {
        if (name == null || name.isBlank()) {
            return false;
        }
        Optional<Recipe> match = recipeRepository.findAllByUserOrderByNameAsc(user).stream()
                .filter(recipe -> recipe.getName().equalsIgnoreCase(name.trim()))
                .findFirst();
        return match.isPresent() && !match.get().getId().equals(allowedId);
    }

    /**
     * Every distinct tag on this account, for the search filter's autocomplete.
     */
    @Transactional(readOnly = true)
    public Set<String> allTags(User user) {
        return recipeRepository.findAllByUserOrderByNameAsc(user).stream()
                .flatMap(recipe -> recipe.getTags().stream())
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
