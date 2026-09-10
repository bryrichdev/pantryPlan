package edu.wgu.pantryplan.service;

import edu.wgu.pantryplan.domain.MealPlan;
import edu.wgu.pantryplan.domain.PlanEntry;
import edu.wgu.pantryplan.domain.Recipe;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.repository.MealPlanRepository;
import edu.wgu.pantryplan.repository.PlanEntryRepository;
import edu.wgu.pantryplan.repository.RecipeRepository;
import edu.wgu.pantryplan.web.form.AutoFillForm;
import edu.wgu.pantryplan.web.form.MealPlanForm;
import edu.wgu.pantryplan.domain.MealSlot;
import edu.wgu.pantryplan.web.form.PlanEntryForm;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
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

    public MealPlanService(MealPlanRepository mealPlanRepository,
                           PlanEntryRepository planEntryRepository,
                           RecipeRepository recipeRepository,
                           PantryCoverageService coverageService) {
        this.mealPlanRepository = mealPlanRepository;
        this.planEntryRepository = planEntryRepository;
        this.recipeRepository = recipeRepository;
        this.coverageService = coverageService;
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
                .sorted(Comparator.comparing(PlanEntry::getMealSlot))
                .toList();
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
        List<Recipe> pool = orderedPool(candidates, user, servings, form.isFavorPantry(), random);
        int cursor = 0;
        int added = 0;

        for (LocalDate date : weekDates(plan)) {
            for (MealSlot slot : MealSlot.values()) {
                if (!form.getSlots().contains(slot)) {
                    continue;
                }
                if (taken.contains(slotKey(date, slot))) {
                    continue;
                }

                if (cursor >= pool.size()) {
                    /* Every recipe has had a turn. Rebuild the order so the next
                       pass through is a different sequence. */
                    pool = orderedPool(candidates, user, servings, form.isFavorPantry(), random);
                    cursor = 0;
                }

                Recipe chosen = form.isAvoidRepeats()
                        ? pool.get(cursor++)
                        : pool.get(random.nextInt(pool.size()));

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
