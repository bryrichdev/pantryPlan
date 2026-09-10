package edu.wgu.pantryplan.service;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.PantryItem;
import edu.wgu.pantryplan.domain.Recipe;
import edu.wgu.pantryplan.domain.RecipeLine;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.repository.PantryItemRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Works out how much of a recipe the pantry already covers.
 *
 * <p>Used to bias auto-filled plans toward what is already on the shelf. The
 * same per-line comparison — scale to the planned servings, convert into the
 * ingredient's stocking unit, measure against what is on hand — is what the
 * grocery list runs to decide what still needs buying.
 */
@Service
public class PantryCoverageService {

    private static final MathContext PRECISION = new MathContext(12, RoundingMode.HALF_UP);

    private final PantryItemRepository pantryItemRepository;
    private final UnitConversionService conversionService;

    public PantryCoverageService(PantryItemRepository pantryItemRepository,
                                 UnitConversionService conversionService) {
        this.pantryItemRepository = pantryItemRepository;
        this.conversionService = conversionService;
    }

    /**
     * Total on the shelf for one ingredient, in that ingredient's stocking unit.
     *
     * <p>Every pantry row for an ingredient is in the same unit by design, so
     * this is plain addition rather than a conversion per row.
     */
    @Transactional(readOnly = true)
    public BigDecimal onHandOf(User user, Ingredient ingredient) {
        List<PantryItem> rows = pantryItemRepository.findAllByUserAndIngredient(user, ingredient);
        BigDecimal total = BigDecimal.ZERO;
        for (PantryItem row : rows) {
            total = total.add(row.getQuantity());
        }
        return total;
    }

    /**
     * How much of one recipe line is needed, expressed in the ingredient's
     * stocking unit so it can be compared with the shelf.
     *
     * @return empty when the recipe's unit cannot be converted to the stocking
     *         unit, which happens when a volume meets a weight and the
     *         ingredient has no weight per cup recorded
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> neededInStockUnit(RecipeLine line, int servings) {
        Ingredient ingredient = line.getIngredient();
        return conversionService.convert(
                line.scaledTo(servings), line.getUnit(), ingredient.getStockUnit(), ingredient);
    }

    /**
     * Whether the shelf holds enough for this line at the planned servings.
     *
     * <p>A line whose units cannot be reconciled counts as not covered. That is
     * the cautious answer: better to suggest buying something already owned than
     * to plan a meal that cannot be cooked.
     */
    @Transactional(readOnly = true)
    public boolean covers(User user, RecipeLine line, int servings) {
        Optional<BigDecimal> needed = neededInStockUnit(line, servings);
        if (needed.isEmpty()) {
            return false;
        }
        return onHandOf(user, line.getIngredient()).compareTo(needed.get()) >= 0;
    }

    /**
     * The share of a recipe's ingredients the pantry can already supply, from
     * 0 to 1.
     *
     * <p>A recipe with no ingredients scores zero rather than one. Nothing is
     * being measured, and scoring it as fully covered would let empty recipes
     * outrank real ones in every suggestion.
     */
    @Transactional(readOnly = true)
    public BigDecimal coverageOf(User user, Recipe recipe, int servings) {
        List<RecipeLine> lines = recipe.getLines();
        if (lines.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int covered = 0;
        for (RecipeLine line : lines) {
            if (covers(user, line, servings)) {
                covered++;
            }
        }
        return BigDecimal.valueOf(covered).divide(BigDecimal.valueOf(lines.size()), PRECISION);
    }
}
