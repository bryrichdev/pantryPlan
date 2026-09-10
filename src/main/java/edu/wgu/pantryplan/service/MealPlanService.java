package edu.wgu.pantryplan.service;

import edu.wgu.pantryplan.domain.MealPlan;
import edu.wgu.pantryplan.domain.PlanEntry;
import edu.wgu.pantryplan.domain.Recipe;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.repository.MealPlanRepository;
import edu.wgu.pantryplan.repository.PlanEntryRepository;
import edu.wgu.pantryplan.repository.RecipeRepository;
import edu.wgu.pantryplan.web.form.MealPlanForm;
import edu.wgu.pantryplan.web.form.PlanEntryForm;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
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

    public MealPlanService(MealPlanRepository mealPlanRepository,
                           PlanEntryRepository planEntryRepository,
                           RecipeRepository recipeRepository) {
        this.mealPlanRepository = mealPlanRepository;
        this.planEntryRepository = planEntryRepository;
        this.recipeRepository = recipeRepository;
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
