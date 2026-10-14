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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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

    /**
     * What copying would do, for the page to explain before the cook commits.
     * The existing copy fields are set when the cook already has a copy of
     * this exact version of the recipe.
     */
    public record CopyPlan(boolean ownRecipe, boolean nameTaken,
                           List<String> reusedIngredients, List<String> newIngredients,
                           Long existingCopyId, String existingCopyName) {

        public boolean alreadyCopied() {
            return existingCopyId != null;
        }
    }

    @Transactional(readOnly = true)
    public List<Recipe> recipesIn(KitchenGroup group) {
        return recipeRepository.findSharedWith(group);
    }

    /**
     * Which of a group's shared recipes this cook already has a copy of, so
     * the page can say "Copied" instead of offering the button again.
     */
    @Transactional(readOnly = true)
    public Set<Long> alreadyCopied(KitchenGroup group, User user) {
        Set<String> copied = new HashSet<>(recipeRepository.findCopiedHashes(user));
        Set<Long> ids = new HashSet<>();
        if (copied.isEmpty()) {
            return ids;
        }
        for (Recipe recipe : recipeRepository.findSharedWith(group)) {
            if (copied.contains(RecipeFingerprint.of(recipe))) {
                ids.add(recipe.getId());
            }
        }
        return ids;
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
        Recipe existing = recipeRepository
                .findFirstByUserAndCopiedFromHash(user, RecipeFingerprint.of(recipe))
                .orElse(null);
        return new CopyPlan(own, nameTaken, new ArrayList<>(reused), new ArrayList<>(created),
                existing == null ? null : existing.getId(),
                existing == null ? null : existing.getName());
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
        String fingerprint = RecipeFingerprint.of(source);
        recipeRepository.findFirstByUserAndCopiedFromHash(user, fingerprint).ifPresent(existing -> {
            throw new GroupRuleException("You already copied this recipe. It is in your recipes as "
                    + existing.getName() + ".");
        });
        String name = newName == null || newName.isBlank() ? source.getName() : newName.trim();
        if (name.length() > 150) {
            throw new GroupRuleException("The name must be 150 characters or fewer.");
        }
        if (recipeRepository.existsByUserAndNameIgnoreCase(user, name)) {
            throw new GroupRuleException("You already have a recipe called " + name
                    + ". Give the copy a different name.");
        }

        return copyRecipe(source, fingerprint, name, user, ingredientsByName(user));
    }

    /**
     * What a bulk action did: how many recipes it handled, any copies that
     * had to take a different name, and anything it left alone, with why.
     */
    public record BulkResult(int done, List<String> renamed, List<String> skipped) {
    }

    /**
     * Copies several shared recipes at once. Recipes that cannot be copied
     * are skipped and reported rather than stopping the rest.
     *
     * <p>A copy keeps its name when the cook has no recipe called that yet.
     * Otherwise it is named after its owner, "Chili (Sam)", the same name the
     * single copy page suggests, and is skipped if that is taken too.
     * Ingredients are matched once for the whole batch, so two recipes that
     * both need cumin create one cumin, not two.
     */
    @Transactional
    public BulkResult copyAllToAccount(Long groupId, Collection<Long> recipeIds, User user) {
        GroupMember mine = groupService.requireMembership(groupId, user);
        Map<String, Ingredient> ingredients = ingredientsByName(user);
        Set<String> copiedHashes = new HashSet<>(recipeRepository.findCopiedHashes(user));
        int copied = 0;
        List<String> renamed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (Long recipeId : new LinkedHashSet<>(recipeIds)) {
            Recipe source = recipeRepository.findSharedRecipe(recipeId, mine.getGroup()).orElse(null);
            if (source == null) {
                skipped.add("one recipe is no longer shared here");
                continue;
            }
            if (source.getUser().getId().equals(user.getId())) {
                skipped.add(source.getName() + " is already yours");
                continue;
            }
            String fingerprint = RecipeFingerprint.of(source);
            if (copiedHashes.contains(fingerprint)) {
                skipped.add(source.getName() + " is already in your recipes");
                continue;
            }
            String name = source.getName();
            if (recipeRepository.existsByUserAndNameIgnoreCase(user, name)) {
                name = ownerSuffixed(source);
                if (recipeRepository.existsByUserAndNameIgnoreCase(user, name)) {
                    skipped.add("you already have " + source.getName() + " and " + name);
                    continue;
                }
                renamed.add(source.getName() + " as " + name);
            }
            copyRecipe(source, fingerprint, name, user, ingredients);
            copiedHashes.add(fingerprint);
            copied++;
        }
        return new BulkResult(copied, renamed, skipped);
    }

    /**
     * Takes several recipes out of a group. Each one is checked on its own: a
     * member's own recipes go, anyone else's are left and reported.
     */
    @Transactional
    public BulkResult unshareAll(Long groupId, Collection<Long> recipeIds, User actor) {
        GroupMember mine = groupService.requireMembership(groupId, actor);
        KitchenGroup group = mine.getGroup();
        int removed = 0;
        List<String> skipped = new ArrayList<>();

        for (Long recipeId : new LinkedHashSet<>(recipeIds)) {
            Recipe recipe = recipeRepository.findSharedRecipe(recipeId, group).orElse(null);
            if (recipe == null) {
                continue;
            }
            if (!canUnshare(mine, recipe, actor)) {
                skipped.add(recipe.getName() + " is not yours to take out");
                continue;
            }
            recipeRepository.deleteShare(recipe.getId(), group.getId());
            removed++;
        }
        return new BulkResult(removed, List.of(), skipped);
    }

    private Recipe copyRecipe(Recipe source, String fingerprint, String name, User user,
                              Map<String, Ingredient> mine) {
        Recipe copy = new Recipe(user, name, source.getServings());
        copy.setCopiedFromHash(fingerprint);
        copy.setDescription(source.getDescription());
        copy.setMealType(source.getMealType());
        copy.setNationality(source.getNationality());
        copy.setPrepMinutes(source.getPrepMinutes());
        copy.setCookMinutes(source.getCookMinutes());
        copy.setInstructions(source.getInstructions());
        copy.setTags(source.getTags());

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

    private Map<String, Ingredient> ingredientsByName(User user) {
        Map<String, Ingredient> byName = new HashMap<>();
        for (Ingredient ingredient : ingredientRepository.findAllByUserOrderByNameAsc(user)) {
            byName.put(key(ingredient.getName()), ingredient);
        }
        return byName;
    }

    /** "Chili (Sam)", trimmed so it still fits the 150-character name column. */
    private static String ownerSuffixed(Recipe source) {
        String suffix = " (" + source.getUser().getDisplayName() + ")";
        String base = source.getName();
        int room = 150 - suffix.length();
        if (room < 1) {
            return base;
        }
        return (base.length() > room ? base.substring(0, room).trim() : base) + suffix;
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
        recipeRepository.deleteShare(recipe.getId(), mine.getGroup().getId());
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
