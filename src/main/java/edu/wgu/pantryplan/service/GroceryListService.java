package edu.wgu.pantryplan.service;

import edu.wgu.pantryplan.domain.GroceryList;
import edu.wgu.pantryplan.domain.GroceryListItem;
import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.MealPlan;
import edu.wgu.pantryplan.domain.PlanEntry;
import edu.wgu.pantryplan.domain.RecipeLine;
import edu.wgu.pantryplan.domain.Unit;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.repository.GroceryListRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a meal plan's grocery list: what the week's meals call for, less what
 * the pantry already holds.
 *
 * <p>Need is added up per ingredient across the whole week before the pantry is
 * subtracted. Checking each recipe line against the shelf on its own would count
 * the same stock once per line, so two meals that each fit inside one bag of
 * flour would both look covered when together they need more than the bag.
 */
@Service
public class GroceryListService {

    /** The number of decimal places the database stores quantities at. */
    private static final int STORED_SCALE = 3;

    private final GroceryListRepository groceryListRepository;
    private final MealPlanService mealPlanService;
    private final PantryCoverageService coverageService;

    public GroceryListService(GroceryListRepository groceryListRepository,
                              MealPlanService mealPlanService,
                              PantryCoverageService coverageService) {
        this.groceryListRepository = groceryListRepository;
        this.mealPlanService = mealPlanService;
        this.coverageService = coverageService;
    }

    @Transactional
    public GroceryList generate(Long planId, User user) {
        return generate(planId, user, LocalDate.now());
    }

    /**
     * Builds the plan's list, replacing any list generated for it before.
     *
     * <p>The list is a snapshot. Once the cook shops and stocks the pantry,
     * generating again drops what was bought, so older lists are replaced
     * rather than kept.
     *
     * @param today the day used to decide which pantry rows have expired
     * @throws java.util.NoSuchElementException when the plan is not this account's
     */
    @Transactional
    public GroceryList generate(Long planId, User user, LocalDate today) {
        MealPlan plan = mealPlanService.requireOwned(planId, user);

        /* Keyed by id rather than by entity. Lines reach their ingredient
           through lazy proxies, and two proxies for one row are not
           guaranteed to be equal to each other. */
        Map<Long, Ingredient> ingredients = new LinkedHashMap<>();
        Map<Long, BigDecimal> needInStockUnit = new LinkedHashMap<>();
        Map<ReviewKey, BigDecimal> needsReview = new LinkedHashMap<>();

        for (PlanEntry entry : mealPlanService.entriesInOrder(plan)) {
            if (entry.isCooked()) {
                continue;
            }
            int servings = entry.getServings();
            for (RecipeLine line : entry.getRecipe().getLines()) {
                Ingredient ingredient = line.getIngredient();
                ingredients.putIfAbsent(ingredient.getId(), ingredient);

                Optional<BigDecimal> needed = coverageService.neededInStockUnit(line, servings);
                if (needed.isPresent()) {
                    needInStockUnit.merge(ingredient.getId(), needed.get(), BigDecimal::add);
                } else {
                    needsReview.merge(new ReviewKey(ingredient.getId(), line.getUnit()),
                            line.scaledTo(servings), BigDecimal::add);
                }
            }
        }

        groceryListRepository.deleteAll(
                groceryListRepository.findAllByMealPlanOrderByGeneratedAtDesc(plan));

        GroceryList list = new GroceryList(user, plan, "Groceries for " + plan.getName(), Instant.now());

        for (Map.Entry<Long, BigDecimal> need : needInStockUnit.entrySet()) {
            Ingredient ingredient = ingredients.get(need.getKey());
            /* Round the week's total to the stored scale before comparing.
               Conversions carry twelve significant digits, so without this a
               difference of 0.0000001 could survive as a line to buy. */
            BigDecimal shortfall = need.getValue()
                    .setScale(STORED_SCALE, RoundingMode.HALF_UP)
                    .subtract(coverageService.onHandOf(user, ingredient, today));
            if (shortfall.signum() > 0) {
                list.addItem(new GroceryListItem(
                        ingredient, shortfall, ingredient.getStockUnit(), ingredient.getCategory()));
            }
        }

        for (Map.Entry<ReviewKey, BigDecimal> flagged : needsReview.entrySet()) {
            Ingredient ingredient = ingredients.get(flagged.getKey().ingredientId());
            /* Rounded up so a tiny scaled amount cannot become zero, which the
               schema rejects. Nothing is subtracted, so this is the recipes'
               full call for the ingredient in that unit. */
            BigDecimal amount = flagged.getValue().setScale(STORED_SCALE, RoundingMode.UP);
            list.addItem(GroceryListItem.needingReview(
                    ingredient, amount, flagged.getKey().unit(), ingredient.getCategory()));
        }

        return groceryListRepository.save(list);
    }

    /**
     * Flagged amounts are grouped by ingredient and unit together. Two recipes
     * calling for cups of basil add up to one row. Cups and tablespoons of it
     * stay as separate rows, each in the unit the recipes wrote, which keeps a
     * flagged row as close as possible to what the cook will read in the recipe.
     */
    private record ReviewKey(Long ingredientId, Unit unit) {
    }
}
