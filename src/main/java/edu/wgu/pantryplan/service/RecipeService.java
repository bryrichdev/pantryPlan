package edu.wgu.pantryplan.service;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.Recipe;
import edu.wgu.pantryplan.domain.RecipeLine;
import edu.wgu.pantryplan.domain.RecipePreset;
import edu.wgu.pantryplan.domain.RecipePresetLine;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.domain.IngredientPreset;
import edu.wgu.pantryplan.repository.IngredientRepository;
import edu.wgu.pantryplan.repository.RecipePresetRepository;
import edu.wgu.pantryplan.repository.PlanEntryRepository;
import edu.wgu.pantryplan.repository.RecipeRepository;
import edu.wgu.pantryplan.web.form.RecipeForm;
import edu.wgu.pantryplan.web.form.RecipeLineForm;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    private final IngredientRepository ingredientRepository;
    private final RecipePresetRepository recipePresetRepository;

    public RecipeService(RecipeRepository recipeRepository,
                         PlanEntryRepository planEntryRepository,
                         IngredientRepository ingredientRepository,
                         RecipePresetRepository recipePresetRepository) {
        this.recipeRepository = recipeRepository;
        this.planEntryRepository = planEntryRepository;
        this.ingredientRepository = ingredientRepository;
        this.recipePresetRepository = recipePresetRepository;
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
        replaceLines(recipe, form, user);
        return recipeRepository.save(recipe);
    }

    @Transactional
    public Recipe update(Long id, RecipeForm form, User user) {
        Recipe recipe = requireOwned(id, user);
        recipe.setName(form.getName().trim());
        recipe.setServings(form.getServings());
        applyForm(recipe, form);
        replaceLines(recipe, form, user);
        return recipeRepository.save(recipe);
    }

    private void applyForm(Recipe recipe, RecipeForm form) {
        recipe.setDescription(emptyToNull(form.getDescription()));
        recipe.setMealType(emptyToNull(form.getMealType()));
        recipe.setNationality(emptyToNull(form.getNationality()));
        recipe.setInstructions(emptyToNull(form.getInstructions()));
        recipe.setPrepMinutes(form.getPrepMinutes() == null ? 0 : form.getPrepMinutes());
        recipe.setCookMinutes(form.getCookMinutes() == null ? 0 : form.getCookMinutes());
        recipe.setTags(form.parsedTags());
    }

    /**
     * Rewrites the ingredient list from the submitted form.
     *
     * <p>Existing rows are dropped and rebuilt rather than matched up and
     * patched. Orphan removal deletes the old rows, and because every
     * replacement is a brand new object with no id, there is no chance of
     * Hibernate trying to delete and re-insert the same row in one flush.
     *
     * <p>Each ingredient is re-read scoped to the owner, so a tampered id
     * belonging to another account fails here rather than silently attaching
     * someone else's record.
     */
    private void replaceLines(Recipe recipe, RecipeForm form, User user) {
        recipe.clearLines();
        for (RecipeLineForm lineForm : form.getLines()) {
            if (lineForm.isBlank()) {
                continue;
            }
            Ingredient ingredient = ingredientRepository
                    .findByIdAndUser(lineForm.getIngredientId(), user)
                    .orElseThrow(() -> new NoSuchElementException(
                            "No ingredient " + lineForm.getIngredientId() + " for this account"));
            RecipeLine line = new RecipeLine(ingredient, lineForm.getQuantity(), lineForm.getUnit());
            line.setNote(emptyToNull(lineForm.getNote()));
            recipe.addLine(line);
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Copies the shared starter recipes into this account. Any ingredients a
     * preset recipe needs are copied from the shared ingredient catalogue too,
     * so its ingredient list is intact even for a brand-new account.
     */
    @Transactional
    public int importPresets(User user) {
        Set<String> recipeNames = new HashSet<>();
        Map<String, Ingredient> ingredientsByName = new HashMap<>();
        for (Recipe existing : recipeRepository.findAllByUserOrderByNameAsc(user)) {
            recipeNames.add(existing.getName().toLowerCase());
        }
        for (Ingredient ingredient : ingredientRepository.findAllByUserOrderByNameAsc(user)) {
            ingredientsByName.put(ingredient.getName().toLowerCase(), ingredient);
        }

        int added = 0;
        for (RecipePreset preset : recipePresetRepository.findAllByOrderByNameAsc()) {
            if (recipeNames.contains(preset.getName().toLowerCase())) {
                continue;
            }
            Recipe recipe = new Recipe(user, preset.getName(), preset.getServings());
            recipe.setDescription(preset.getDescription());
            recipe.setMealType(preset.getMealType());
            recipe.setNationality(preset.getNationality());
            recipe.setPrepMinutes(preset.getPrepMinutes());
            recipe.setCookMinutes(preset.getCookMinutes());
            recipe.setInstructions(preset.getInstructions());
            recipe.setTags(preset.getTags());
            for (RecipePresetLine presetLine : preset.getLines()) {
                IngredientPreset presetIngredient = presetLine.getIngredientPreset();
                String key = presetIngredient.getName().toLowerCase();
                Ingredient ingredient = ingredientsByName.get(key);
                if (ingredient == null) {
                    ingredient = copyIngredientPreset(user, presetIngredient);
                    ingredientsByName.put(key, ingredient);
                }
                RecipeLine line = new RecipeLine(ingredient, presetLine.getQuantity(), presetLine.getUnit());
                line.setNote(presetLine.getNote());
                recipe.addLine(line);
            }
            recipeRepository.save(recipe);
            recipeNames.add(preset.getName().toLowerCase());
            added++;
        }
        return added;
    }

    @Transactional(readOnly = true)
    public int countMissingPresets(User user) {
        Set<String> recipeNames = recipeRepository.findAllByUserOrderByNameAsc(user).stream()
                .map(recipe -> recipe.getName().toLowerCase())
                .collect(Collectors.toSet());
        return (int) recipePresetRepository.findAllByOrderByNameAsc().stream()
                .filter(preset -> !recipeNames.contains(preset.getName().toLowerCase()))
                .count();
    }

    private Ingredient copyIngredientPreset(User user, IngredientPreset preset) {
        Ingredient ingredient = new Ingredient(user, preset.getName(), preset.getCategory());
        ingredient.setStockUnit(preset.getStockUnit());
        ingredient.setDefaultLocation(preset.getDefaultLocation());
        ingredient.setDefaultQuantity(preset.getDefaultQuantity());
        ingredient.setGramsPerCup(preset.getGramsPerCup());
        return ingredientRepository.save(ingredient);
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
