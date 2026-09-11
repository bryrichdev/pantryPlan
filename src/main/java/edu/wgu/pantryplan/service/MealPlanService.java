package edu.wgu.pantryplan.service;

import edu.wgu.pantryplan.domain.CookLog;
import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.MealPlan;
import edu.wgu.pantryplan.domain.PlanEntry;
import edu.wgu.pantryplan.domain.PantryItem;
import edu.wgu.pantryplan.domain.Recipe;
import edu.wgu.pantryplan.domain.RecipeLine;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.repository.CookLogRepository;
import edu.wgu.pantryplan.repository.MealPlanRepository;
import edu.wgu.pantryplan.repository.PantryItemRepository;
import edu.wgu.pantryplan.repository.PlanEntryRepository;
import edu.wgu.pantryplan.repository.RecipeRepository;
import edu.wgu.pantryplan.web.form.AutoFillForm;
import edu.wgu.pantryplan.web.form.MealPlanForm;
import edu.wgu.pantryplan.domain.MealSlot;
import edu.wgu.pantryplan.web.form.PlanEntryForm;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Weekly meal plans for a single cook.
 */
@Service
public class MealPlanService {

    private final MealPlanRepository mealPlanRepository;
    private final PlanEntryRepository planEntryRepository;
    private final RecipeRepository recipeRepository;
    private final PantryCoverageService coverageService;
    private final UnitConversionService unitConversionService;
    private final PantryItemRepository pantryItemRepository;
    private final CookLogRepository cookLogRepository;

    public MealPlanService(MealPlanRepository mealPlanRepository,
                           PlanEntryRepository planEntryRepository,
                           RecipeRepository recipeRepository,
                           PantryCoverageService coverageService,
                           UnitConversionService unitConversionService,
                           PantryItemRepository pantryItemRepository,
                           CookLogRepository cookLogRepository) {
        this.mealPlanRepository = mealPlanRepository;
        this.planEntryRepository = planEntryRepository;
        this.recipeRepository = recipeRepository;
        this.coverageService = coverageService;
        this.unitConversionService = unitConversionService;
        this.pantryItemRepository = pantryItemRepository;
        this.cookLogRepository = cookLogRepository;
    }

    @Transactional(readOnly = true)
    public List<MealPlan> findAll(User user) {
        List<MealPlan> plans = mealPlanRepository.findAllByUserOrderByWeekStartDateDesc(user);
        plans.forEach(plan -> plan.getEntries().size());
        return plans;
    }

    /**
     * Loads a plan with its entries and each entry's recipe name resolved, since
     * open-in-view is off and the builder page reads all of it.
     */
    @Transactional(readOnly = true)
    public MealPlan requireOwned(Long id, User user) {
        MealPlan plan = mealPlanRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new NoSuchElementException("No meal plan " + id + " for this account"));
        plan.getEntries().forEach(entry -> entry.getRecipe().getName());
        return plan;
    }

    /**
     * The plan's entries in day then meal order, which is how the builder shows
     * them and the order the grocery list walks them in.
     */
    @Transactional(readOnly = true)
    public List<PlanEntry> entriesInOrder(MealPlan plan) {
        List<PlanEntry> entries = planEntryRepository
                .findAllByMealPlanOrderByPlanDateAscMealSlotAsc(plan);
        /* meal_slot is persisted as text, so SQL's alphabetical order would
           put dinner ahead of lunch. Keep the display order intentional. */
        entries.sort(Comparator.comparing(PlanEntry::getPlanDate)
                .thenComparingInt(entry -> mealSlotOrder(entry.getMealSlot())));
        entries.forEach(entry -> entry.getRecipe().getName());
        return entries;
    }

    /**
     * Entries falling on one date, in meal order.
     */
    @Transactional(readOnly = true)
    public List<PlanEntry> entriesOn(MealPlan plan, LocalDate date) {
        return entriesInOrder(plan).stream()
                .filter(entry -> date.equals(entry.getPlanDate()))
                .sorted(Comparator.comparingInt(entry -> mealSlotOrder(entry.getMealSlot())))
                .toList();
    }

    private int mealSlotOrder(MealSlot slot) {
        return switch (slot) {
            case BREAKFAST -> 0;
            case LUNCH -> 1;
            case DINNER -> 2;
            case SNACK -> 3;
        };
    }

    @Transactional
    public MealPlan create(MealPlanForm form, User user) {
        MealPlan plan = new MealPlan(user, form.getName().trim(), form.getWeekStartDate());
        return mealPlanRepository.save(plan);
    }

    @Transactional
    public MealPlan update(Long id, MealPlanForm form, User user) {
        MealPlan plan = requireOwned(id, user);
        plan.setName(form.getName().trim());
        plan.setWeekStartDate(form.getWeekStartDate());
        return mealPlanRepository.save(plan);
    }

    @Transactional
    public void delete(Long id, User user) {
        mealPlanRepository.delete(requireOwned(id, user));
    }

    /**
     * Adds a recipe to a day and meal.
     *
     * <p>The recipe is re-read scoped to the owner, so a tampered id belonging
     * to another account fails here rather than being scheduled silently.
     *
     * <p>The entry is saved through its own repository rather than by cascading
     * from the plan. The plan already has an id, so saving it goes through
     * merge, and a cascaded merge assigns the generated id to a managed copy —
     * leaving the instance built here without one. Persisting the child
     * directly puts the id on the object the caller receives.
     */
    @Transactional
    public PlanEntry addEntry(Long planId, PlanEntryForm form, User user) {
        MealPlan plan = requireOwned(planId, user);
        Recipe recipe = recipeRepository.findByIdAndUser(form.getRecipeId(), user)
                .orElseThrow(() -> new NoSuchElementException(
                        "No recipe " + form.getRecipeId() + " for this account"));

        PlanEntry entry = new PlanEntry(recipe, form.getPlanDate(), form.getMealSlot(), form.getServings());
        plan.addEntry(entry);
        return planEntryRepository.save(entry);
    }

    /**
     * Removes one scheduled meal. The entry is looked up through its plan so an
     * id from another account cannot be removed.
     */
    @Transactional
    public void removeEntry(Long planId, Long entryId, User user) {
        MealPlan plan = requireOwned(planId, user);
        PlanEntry entry = planEntryRepository.findByIdAndMealPlan(entryId, plan)
                .orElseThrow(() -> new NoSuchElementException(
                        "No entry " + entryId + " on this plan"));
        plan.removeEntry(entry);
        mealPlanRepository.save(plan);
    }

    /**
     * Removes the selected meals from a plan. IDs outside this plan (including
     * another account's) are ignored and reported as missing.
     */
    @Transactional
    public BulkDeleteResult removeEntries(Long planId, java.util.Collection<Long> entryIds, User user) {
        MealPlan plan = requireOwned(planId, user);
        BulkDeleteResult result = new BulkDeleteResult();
        if (entryIds == null) {
            return result;
        }

        Set<Long> uniqueIds = new HashSet<>(entryIds);
        uniqueIds.remove(null);
        for (Long entryId : uniqueIds) {
            PlanEntry entry = planEntryRepository.findByIdAndMealPlan(entryId, plan).orElse(null);
            if (entry == null) {
                result.recordMissing();
                continue;
            }
            plan.removeEntry(entry);
            result.recordDeleted();
        }
        mealPlanRepository.save(plan);
        return result;
    }

    /**
     * Marks selected scheduled meals as cooked and deducts their recipe lines
     * from the owner's pantry. A pantry row is never allowed below zero; rows
     * emptied by the deduction are removed entirely. Expired rows are never
     * drawn from, which matches the grocery list treating them as unusable.
     */
    @Transactional
    public CookResult markEntriesCooked(Long planId, java.util.Collection<Long> entryIds, User user) {
        MealPlan plan = requireOwned(planId, user);
        CookResult result = new CookResult();
        if (entryIds == null) {
            return result;
        }

        Instant cookedAt = Instant.now();
        Set<Long> uniqueIds = new HashSet<>(entryIds);
        uniqueIds.remove(null);
        for (Long entryId : uniqueIds) {
            PlanEntry entry = planEntryRepository.findByIdAndMealPlan(entryId, plan).orElse(null);
            if (entry == null) {
                result.recordMissing();
                continue;
            }
            if (entry.isCooked()) {
                result.recordAlreadyCooked();
                continue;
            }
            cook(entry, user, cookedAt, result);
        }
        return result;
    }

    /** Marks one entry cooked; see {@link #markEntriesCooked(Long, java.util.Collection, User)}. */
    @Transactional
    public CookResult markEntryCooked(Long planId, Long entryId, User user) {
        List<Long> entryIds = new ArrayList<>();
        entryIds.add(entryId);
        return markEntriesCooked(planId, entryIds, user);
    }

    /**
     * Reverses selected cook actions. The stock amount restored comes from the
     * cook logs, so a meal that was only partly covered restores only what was
     * actually removed from the pantry.
     */
    @Transactional
    public CookUndoResult markEntriesNotCooked(Long planId,
                                               java.util.Collection<Long> entryIds,
                                               User user) {
        MealPlan plan = requireOwned(planId, user);
        CookUndoResult result = new CookUndoResult();
        if (entryIds == null) {
            return result;
        }

        Set<Long> uniqueIds = new HashSet<>(entryIds);
        uniqueIds.remove(null);
        for (Long entryId : uniqueIds) {
            PlanEntry entry = planEntryRepository.findByIdAndMealPlan(entryId, plan).orElse(null);
            if (entry == null) {
                result.recordMissing();
                continue;
            }
            if (!entry.isCooked()) {
                result.recordAlreadyUncooked();
                continue;
            }
            undoCooking(entry, user, result);
        }
        return result;
    }

    /** Reverses one entry; see {@link #markEntriesNotCooked(Long, java.util.Collection, User)}. */
    @Transactional
    public CookUndoResult markEntryNotCooked(Long planId, Long entryId, User user) {
        List<Long> entryIds = new ArrayList<>();
        entryIds.add(entryId);
        return markEntriesNotCooked(planId, entryIds, user);
    }

    private void cook(PlanEntry entry, User user, Instant cookedAt, CookResult result) {
        Recipe recipe = entry.getRecipe();
        for (RecipeLine line : recipe.getLines()) {
            BigDecimal needed = coverageService.neededInStockUnit(line, entry.getServings()).orElse(null);
            if (needed == null) {
                result.recordUnconvertibleLine();
                continue;
            }
            deductIngredient(user, entry, line.getIngredient(), needed, cookedAt);
        }

        entry.markCooked(cookedAt);
        recipe.recordCooked(cookedAt);
        planEntryRepository.save(entry);
        recipeRepository.save(recipe);
        result.recordCooked();
    }

    private void deductIngredient(User user, PlanEntry entry, Ingredient ingredient,
                                  BigDecimal requested, Instant cookedAt) {
        BigDecimal remaining = requested.setScale(3, RoundingMode.HALF_UP);
        LocalDate today = LocalDate.now();
        List<PantryItem> pantryRows = new ArrayList<>(
                pantryItemRepository.findAllByUserAndIngredient(user, ingredient));
        pantryRows.removeIf(item -> item.isExpired(today));
        pantryRows.sort(Comparator
                .comparing(PantryItem::getExpiresOn, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PantryItem::getPurchasedOn, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PantryItem::getId));

        for (PantryItem item : pantryRows) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal available = item.getQuantity();
            BigDecimal deducted = available.min(remaining);
            item.deduct(deducted);
            remaining = remaining.subtract(deducted);

            if (deducted.signum() > 0) {
                cookLogRepository.save(new CookLog(user, entry, ingredient, deducted,
                        ingredient.getStockUnit(), cookedAt));
            }
            if (item.getQuantity().signum() == 0) {
                pantryItemRepository.delete(item);
            }
        }
    }

    private void undoCooking(PlanEntry entry, User user, CookUndoResult result) {
        List<CookLog> logs = cookLogRepository.findAllByPlanEntryAndReversedFalse(entry);
        Map<Long, RestoredIngredient> restored = new LinkedHashMap<>();

        /* Validate every conversion before changing anything. An ingredient's
           stock unit can be edited after a meal was cooked; in that unusual
           case an unsafe restoration is better refused than guessed. */
        for (CookLog log : logs) {
            Ingredient ingredient = log.getIngredient();
            Optional<BigDecimal> amount = unitConversionService.convert(
                    log.getQuantityDeducted(), log.getUnit(), ingredient.getStockUnit(), ingredient);
            if (amount.isEmpty()) {
                result.recordUnrestorableLog();
                return;
            }
            RestoredIngredient existing = restored.get(ingredient.getId());
            BigDecimal total = amount.get().setScale(3, RoundingMode.HALF_UP);
            if (existing != null) {
                total = total.add(existing.quantity());
            }
            restored.put(ingredient.getId(), new RestoredIngredient(ingredient, total));
        }

        /* Cook logs record the amount, not the original pantry row. Restored
           stock therefore becomes one fresh row in the ingredient's default
           location, with no invented purchase or expiry date. */
        for (RestoredIngredient restoration : restored.values()) {
            if (restoration.quantity().signum() > 0) {
                pantryItemRepository.save(new PantryItem(user, restoration.ingredient(),
                        restoration.quantity().setScale(3, RoundingMode.HALF_UP)));
            }
        }
        for (CookLog log : logs) {
            log.markReversed();
            cookLogRepository.save(log);
        }

        entry.markNotCooked();
        planEntryRepository.save(entry);
        planEntryRepository.flush();
        reconcileRecipeCookingHistory(entry.getRecipe());
        result.recordUncooked();
    }

    private void reconcileRecipeCookingHistory(Recipe recipe) {
        int cookedCount = Math.toIntExact(planEntryRepository.countByRecipeAndCookedTrue(recipe));
        Instant mostRecentCookedAt = planEntryRepository
                .findFirstByRecipeAndCookedTrueOrderByCookedAtDesc(recipe)
                .map(PlanEntry::getCookedAt)
                .orElse(null);
        recipe.reconcileCookingHistory(cookedCount, mostRecentCookedAt);
        recipeRepository.save(recipe);
    }

    private record RestoredIngredient(Ingredient ingredient, BigDecimal quantity) {
    }


    /**
     * Fills a week with recipes chosen for the cook.
     *
     * @return how many meals were scheduled
     */
    @Transactional
    public int autoFill(Long planId, AutoFillForm form, User user) {
        return autoFill(planId, form, user, new Random());
    }

    /**
     * Fills a week, taking the source of randomness as an argument.
     *
     * <p>Two knobs shape the result. Favouring the pantry ranks candidates by
     * how much of each the shelf already covers, so a week gets planned around
     * what is in the house. Avoiding repeats works through every recipe before
     * any comes round again, which matters because seven dinners and three
     * recipes must repeat eventually — the question is only when.
     *
     * <p>The seeded overload exists so tests can assert on an exact outcome
     * rather than on properties of a shuffle.
     */
    @Transactional
    public int autoFill(Long planId, AutoFillForm form, User user, Random random) {
        MealPlan plan = requireOwned(planId, user);
        List<Recipe> candidates = recipeRepository.findAllByUserOrderByNameAsc(user);
        if (candidates.isEmpty()) {
            return 0;
        }

        List<PlanEntry> existing = entriesInOrder(plan);
        Set<String> taken = new HashSet<>();
        for (PlanEntry entry : existing) {
            taken.add(slotKey(entry.getPlanDate(), entry.getMealSlot()));
        }

        if (form.isReplaceExisting()) {
            for (PlanEntry entry : existing) {
                if (form.getSlots().contains(entry.getMealSlot())) {
                    plan.removeEntry(entry);
                    taken.remove(slotKey(entry.getPlanDate(), entry.getMealSlot()));
                }
            }
            planEntryRepository.flush();
        }

        int servings = form.getServings() == null ? 4 : form.getServings();
        /* Every slot owns its pool and cursor. A shared pool would let a
           breakfast recipe slide into dinner simply because it was next in
           the shuffled order. */
        Map<MealSlot, List<Recipe>> pools = new EnumMap<>(MealSlot.class);
        Map<MealSlot, Integer> cursors = new EnumMap<>(MealSlot.class);
        int added = 0;

        for (LocalDate date : weekDates(plan)) {
            for (MealSlot slot : MealSlot.values()) {
                if (!form.getSlots().contains(slot)) {
                    continue;
                }
                if (taken.contains(slotKey(date, slot))) {
                    continue;
                }

                List<Recipe> matchingCandidates = candidates.stream()
                        .filter(recipe -> isCompatibleWithSlot(recipe, slot))
                        .toList();
                if (matchingCandidates.isEmpty()) {
                    continue;
                }

                List<Recipe> pool = pools.get(slot);
                if (pool == null || cursors.get(slot) >= pool.size()) {
                    /* Every recipe has had a turn. Rebuild the order so the next
                       pass through is a different sequence. */
                    pool = orderedPool(matchingCandidates, user, servings, form.isFavorPantry(), random);
                    pools.put(slot, pool);
                    cursors.put(slot, 0);
                }

                Recipe chosen;
                if (form.isAvoidRepeats()) {
                    int cursor = cursors.get(slot);
                    chosen = pool.get(cursor);
                    cursors.put(slot, cursor + 1);
                } else {
                    chosen = pool.get(random.nextInt(pool.size()));
                }

                PlanEntry entry = new PlanEntry(chosen, date, slot, servings);
                plan.addEntry(entry);
                planEntryRepository.save(entry);
                taken.add(slotKey(date, slot));
                added++;
            }
        }
        return added;
    }

    /**
     * Shuffles the candidates, then optionally sorts by pantry coverage.
     *
     * <p>The shuffle happens first and the sort is stable, so recipes the pantry
     * covers equally well still come out in a different order each time. Sorting
     * alone would produce the same week on every run.
     */
    private List<Recipe> orderedPool(List<Recipe> candidates, User user, int servings,
                                     boolean favorPantry, Random random) {
        List<Recipe> pool = new ArrayList<>(candidates);
        java.util.Collections.shuffle(pool, random);
        if (favorPantry) {
            pool.sort(Comparator.comparing(
                    (Recipe recipe) -> coverageService.coverageOf(user, recipe, servings)).reversed());
        }
        return pool;
    }

    /**
     * Recipes classify themselves by their intended meal type. Accounts that
     * created recipes before classification was introduced have a blank type;
     * those legacy recipes remain eligible for every slot until the cook labels
     * them, while an explicitly labelled recipe must match the slot exactly.
     */
    private boolean isCompatibleWithSlot(Recipe recipe, MealSlot slot) {
        String mealType = recipe.getMealType();
        return mealType == null || mealType.isBlank()
                || mealType.trim().equalsIgnoreCase(slot.name());
    }

    private String slotKey(LocalDate date, MealSlot slot) {
        return date + "/" + slot;
    }

    /**
     * True when the date falls inside the plan's seven day window.
     */
    public boolean dateIsInWeek(MealPlan plan, LocalDate date) {
        if (date == null) {
            return false;
        }
        return !date.isBefore(plan.getWeekStartDate()) && !date.isAfter(plan.weekEndDate());
    }

    /**
     * The seven dates the plan covers.
     */
    public List<LocalDate> weekDates(MealPlan plan) {
        return java.util.stream.IntStream.rangeClosed(0, 6)
                .mapToObj(offset -> plan.getWeekStartDate().plusDays(offset))
                .toList();
    }
}
