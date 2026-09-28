package edu.wgu.pantryprep.service;

import edu.wgu.pantryprep.domain.GroceryList;
import edu.wgu.pantryprep.domain.GroceryListItem;
import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.MealPlan;
import edu.wgu.pantryprep.domain.PantryItem;
import edu.wgu.pantryprep.domain.PlanEntry;
import edu.wgu.pantryprep.domain.RecipeLine;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.repository.GroceryListItemRepository;
import edu.wgu.pantryprep.repository.GroceryListRepository;
import edu.wgu.pantryprep.web.form.StockUpForm;
import edu.wgu.pantryprep.web.form.StockUpRow;
import edu.wgu.pantryprep.web.form.PantryItemForm;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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

    /**
     * Aisle order then name, matching how the detail page lists things, so the
     * stock-up dialog runs in the order the cook walked the shop.
     */
    private static final Comparator<GroceryListItem> SHELF_ORDER =
            Comparator.comparing(GroceryListItem::getCategory)
                    .thenComparing(item -> item.getIngredient().getName(), String.CASE_INSENSITIVE_ORDER);

    private final GroceryListRepository groceryListRepository;
    private final GroceryListItemRepository groceryListItemRepository;
    private final MealPlanService mealPlanService;
    private final PantryCoverageService coverageService;
    private final PantryService pantryService;

    public GroceryListService(GroceryListRepository groceryListRepository,
                              GroceryListItemRepository groceryListItemRepository,
                              MealPlanService mealPlanService,
                              PantryCoverageService coverageService,
                              PantryService pantryService) {
        this.groceryListRepository = groceryListRepository;
        this.groceryListItemRepository = groceryListItemRepository;
        this.mealPlanService = mealPlanService;
        this.coverageService = coverageService;
        this.pantryService = pantryService;
    }

    /**
     * Lines that could still be put away, ticked or not, in the order they
     * appear on the list.
     *
     * <p>The stock-up dialog is built from all of them so that ticking an item
     * only shows or hides a row the browser already has, rather than fetching
     * the page again.
     */
    @Transactional(readOnly = true)
    public List<GroceryListItem> stockable(GroceryList list) {
        return list.getItems().stream()
                .filter(item -> !item.isStocked())
                .sorted(SHELF_ORDER)
                .toList();
    }

    /**
     * Takes one line back off the shelf: the pantry row it created is deleted
     * and the line becomes bought but not put away, ready to stock again.
     *
     * <p>If that row has already been deleted from the pantry page, the link is
     * null and only the mark is cleared. Undoing is about this line, not about
     * whatever the shelf looks like now.
     *
     * @return true if a pantry row was deleted, false if there was none left
     * @throws NoSuchElementException when the line is not this account's, or
     *     was never put away
     */
    @Transactional
    public boolean undoStock(Long listId, Long itemId, User user) {
        GroceryList list = requireOwned(listId, user);
        GroceryListItem item = list.getItems().stream()
                .filter(candidate -> candidate.getId().equals(itemId))
                .filter(GroceryListItem::isStocked)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "No stocked item " + itemId + " on this list"));

        PantryItem stored = item.getPantryItem();
        /* The link is cleared first. The pantry row is deleted next, and the
           database would otherwise refuse while this line still points at it. */
        item.clearStock();
        groceryListItemRepository.saveAndFlush(item);

        if (stored == null) {
            return false;
        }
        pantryService.delete(stored.getId(), user);
        return true;
    }

    /** Bought lines waiting to be put away. */
    @Transactional(readOnly = true)
    public List<GroceryListItem> readyToStock(GroceryList list) {
        return stockable(list).stream().filter(GroceryListItem::isPurchased).toList();
    }

    /**
     * Puts a shop away: one pantry row per ticked line, then each line is
     * marked stocked so it cannot be added again.
     *
     * <p>The whole thing is one transaction. If any row is rejected, nothing
     * reaches the pantry and nothing is marked, so the cook can correct the
     * dialog and submit it once.
     *
     * @return how many shelf entries were created
     * @throws NoSuchElementException when a row names a line that is not on
     *     this list, or one that has already been stocked
     */
    @Transactional
    public int stockUp(Long listId, StockUpForm form, User user) {
        GroceryList list = requireOwned(listId, user);
        Instant now = Instant.now();
        int stocked = 0;

        for (StockUpRow row : form.included()) {
            GroceryListItem item = list.getItems().stream()
                    .filter(candidate -> candidate.getId().equals(row.getItemId()))
                    .filter(GroceryListItem::isReadyToStock)
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException(
                            "No bought item " + row.getItemId() + " waiting on this list"));

            PantryItemForm pantryForm = new PantryItemForm();
            /* The ingredient id is re-read from the saved line rather than
               taken from the request, so a tampered row cannot stock someone
               else's ingredient. PantryService scopes it to the owner again. */
            pantryForm.setIngredientId(item.getIngredient().getId());
            pantryForm.setQuantity(row.getQuantity());
            pantryForm.setLocation(row.getLocation());
            pantryForm.setPurchasedOn(form.getPurchasedOn());
            pantryForm.setExpiresOn(row.getExpiresOn());
            PantryItem created = pantryService.create(pantryForm, user);

            item.markStocked(now, created, row.getQuantity());
            groceryListItemRepository.save(item);
            stocked++;
        }
        return stocked;
    }

    /**
     * Every list on the account, newest first, with what the list page reads
     * already loaded. open-in-view is off, so the template cannot load it later.
     */
    @Transactional(readOnly = true)
    public List<GroceryList> findAll(User user) {
        List<GroceryList> lists = groceryListRepository.findAllByUserOrderByGeneratedAtDesc(user);
        lists.forEach(GroceryListService::loadForDisplay);
        return lists;
    }

    /**
     * The plan's current list, if one has been built. The caller has already
     * checked the plan belongs to the account.
     */
    @Transactional(readOnly = true)
    public Optional<GroceryList> findForPlan(MealPlan plan) {
        return groceryListRepository.findFirstByMealPlanOrderByGeneratedAtDesc(plan);
    }

    /**
     * One list, scoped to its owner, with its items and their ingredients loaded.
     *
     * @throws NoSuchElementException when the list is not this account's
     */
    @Transactional(readOnly = true)
    public GroceryList requireOwned(Long id, User user) {
        GroceryList list = findOwned(id, user);
        loadForDisplay(list);
        return list;
    }

    /**
     * Ticks an item off, or unticks it. The item is looked up through its list,
     * and the list through its owner, so an id from another account fails.
     */
    @Transactional
    public GroceryListItem togglePurchased(Long listId, Long itemId, User user) {
        GroceryList list = findOwned(listId, user);
        GroceryListItem item = groceryListItemRepository.findByIdAndGroceryList(itemId, list)
                .orElseThrow(() -> new NoSuchElementException("No item " + itemId + " on this list"));
        item.togglePurchased();
        return groceryListItemRepository.save(item);
    }

    @Transactional
    public void delete(Long id, User user) {
        groceryListRepository.delete(findOwned(id, user));
    }

    private GroceryList findOwned(Long id, User user) {
        return groceryListRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new NoSuchElementException("No grocery list " + id + " for this account"));
    }

    private static void loadForDisplay(GroceryList list) {
        if (list.getMealPlan() != null) {
            list.getMealPlan().getName();
        }
        list.getItems().forEach(item -> item.getIngredient().getName());
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
