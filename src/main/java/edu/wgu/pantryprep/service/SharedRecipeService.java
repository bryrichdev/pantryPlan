package edu.wgu.pantryprep.service;

import edu.wgu.pantryprep.domain.GroupMember;
import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.KitchenGroup;
import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.RecipeLine;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.repository.IngredientRepository;
import edu.wgu.pantryprep.repository.RecipeRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recipes as group members see them: read-only, and copyable.
 *
 * <p>A shared recipe stays in its owner's account, pointing at the owner's
 * ingredients. Another member who wants to plan or cook it copies it. The copy
 * is an ordinary recipe of theirs, and each of its ingredients is matched by
 * name to one they already have, so it lines up with their own pantry. Only
 * ingredients they do not have yet are created, carrying over the owner's
 * category, stock unit, and weight per cup.
 *
 * <p>Names are unique per account ignoring case, which is what makes matching
 * by name safe, and why a copy whose name is taken needs a new one.
 */
@Service
public class SharedRecipeService {

    private final GroupService groupService;
    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;

    public SharedRecipeService(GroupService groupService,
                               RecipeRepository recipeRepository,
                               IngredientRepository ingredientRepository) {
        this.groupService = groupService;
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
    }

    /** What copying would do, for the page to explain before the cook commits. */
    public record CopyPlan(boolean ownRecipe, boolean nameTaken,
                           List<String> reusedIngredients, List<String> newIngredients) {
    }

    @Transactional(readOnly = true)
    public List<Recipe> recipesIn(KitchenGroup group) {
        return recipeRepository.findSharedWith(group);
    }

    /**
     * A recipe shared into a group the user is in, loaded for display.
     *
     * @throws NoSuchElementException when the user is not in the group or the
     *         recipe is not shared there
     */
    @Transactional(readOnly = true)
    public Recipe requireShared(Long groupId, Long recipeId, User user) {
        GroupMember mine = groupService.requireMembership(groupId, user);
        Recipe recipe = recipeRepository.findSharedRecipe(recipeId, mine.getGroup())
                .orElseThrow(() -> new NoSuchElementException("No recipe " + recipeId + " in this group"));
        recipe.getTags().size();
        recipe.getLines().forEach(line -> line.getIngredient().getName());
        return recipe;
    }

    @Transactional(readOnly = true)
    public CopyPlan planCopy(Recipe recipe, User user) {
        boolean own = recipe.getUser().getId().equals(user.getId());
        boolean nameTaken = recipeRepository.existsByUserAndNameIgnoreCase(user, recipe.getName());
        Set<String> reused = new LinkedHashSet<>();
        Set<String> created = new LinkedHashSet<>();
        for (RecipeLine line : recipe.getLines()) {
            String name = line.getIngredient().getName();
            if (ingredientRepository.existsByUserAndNameIgnoreCase(user, name)) {
                reused.add(name);
            } else {
                created.add(name);
            }
        }
        return new CopyPlan(own, nameTaken, new ArrayList<>(reused), new ArrayList<>(created));
    }

    /**
     * Copies a shared recipe into the user's account.
     *
     * @param newName the copy's name, or blank to keep the original's
     * @throws GroupRuleException when it is already the user's recipe, or the
     *         name is taken or too long
     */
    @Transactional
    public Recipe copyToAccount(Long groupId, Long recipeId, String newName, User user) {
        Recipe source = requireShared(groupId, recipeId, user);
        if (source.getUser().getId().equals(user.getId())) {
            throw new GroupRuleException("This is already one of your recipes.");
        }
        String name = newName == null || newName.isBlank() ? source.getName() : newName.trim();
        if (name.length() > 150) {
            throw new GroupRuleException("The name must be 150 characters or fewer.");
        }
        if (recipeRepository.existsByUserAndNameIgnoreCase(user, name)) {
            throw new GroupRuleException("You already have a recipe called " + name
                    + ". Give the copy a different name.");
        }

        Recipe copy = new Recipe(user, name, source.getServings());
        copy.setDescription(source.getDescription());
        copy.setMealType(source.getMealType());
        copy.setNationality(source.getNationality());
        copy.setPrepMinutes(source.getPrepMinutes());
        copy.setCookMinutes(source.getCookMinutes());
        copy.setInstructions(source.getInstructions());
        copy.setTags(source.getTags());

        Map<String, Ingredient> mine = new HashMap<>();
        for (Ingredient ingredient : ingredientRepository.findAllByUserOrderByNameAsc(user)) {
            mine.put(key(ingredient.getName()), ingredient);
        }
        for (RecipeLine line : source.getLines()) {
            Ingredient theirs = line.getIngredient();
            Ingredient ingredient = mine.computeIfAbsent(key(theirs.getName()),
                    unused -> ingredientRepository.save(copyOf(theirs, user)));
            RecipeLine copiedLine = new RecipeLine(ingredient, line.getQuantity(), line.getUnit());
            copiedLine.setNote(line.getNote());
            copy.addLine(copiedLine);
        }
        return recipeRepository.save(copy);
    }

    /**
     * Takes a recipe out of a group. Its owner can always do this; owners and
     * admins can do it to anyone's.
     */
    @Transactional
    public void unshare(Long groupId, Long recipeId, User actor) {
        GroupMember mine = groupService.requireMembership(groupId, actor);
        Recipe recipe = recipeRepository.findSharedRecipe(recipeId, mine.getGroup())
                .orElseThrow(() -> new NoSuchElementException("No recipe " + recipeId + " in this group"));
        boolean ownsRecipe = recipe.getUser().getId().equals(actor.getId());
        if (!ownsRecipe && !mine.getRole().canManageMembers()) {
            throw new GroupPermissionException("A " + mine.getRole() + " cannot remove other people's recipes");
        }
        recipe.getSharedWithGroups().removeIf(group -> group.getId().equals(groupId));
        recipeRepository.save(recipe);
    }

    /** Whether this member may take this recipe out of the group. */
    public boolean canUnshare(GroupMember member, Recipe recipe, User actor) {
        return recipe.getUser().getId().equals(actor.getId())
                || member.getRole().canManageMembers();
    }

    private static Ingredient copyOf(Ingredient theirs, User user) {
        Ingredient ingredient = new Ingredient(user, theirs.getName(), theirs.getCategory());
        ingredient.setStockUnit(theirs.getStockUnit());
        ingredient.setDefaultLocation(theirs.getDefaultLocation());
        ingredient.setDefaultQuantity(theirs.getDefaultQuantity());
        ingredient.setGramsPerCup(theirs.getGramsPerCup());
        return ingredient;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
