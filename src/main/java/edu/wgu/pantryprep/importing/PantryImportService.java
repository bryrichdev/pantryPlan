package edu.wgu.pantryprep.importing;

import static edu.wgu.pantryprep.importing.ImportWorkbooks.AMOUNT;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.CATEGORY;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.EXPIRES;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.INGREDIENT;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.LOCATION;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.PANTRY;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.PANTRY_WORKBOOK;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.PURCHASED;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.UNIT;

import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.IngredientCategory;
import edu.wgu.pantryprep.domain.StorageLocation;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.importing.ImportReport.SheetCount;
import edu.wgu.pantryprep.importing.WorkbookSpec.Sheet;
import edu.wgu.pantryprep.repository.IngredientRepository;
import edu.wgu.pantryprep.service.PantryService;
import edu.wgu.pantryprep.service.UnitConversionService;
import edu.wgu.pantryprep.web.form.PantryItemForm;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a filled-in pantry spreadsheet into pantry items, added alongside
 * whatever is already on the shelves. Amounts are stored in the ingredient's
 * stock unit, so a row in another unit is converted the way the shelf would
 * convert it. A preview saves nothing; an import saves every row or none.
 */
@Service
public class PantryImportService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999.999");

    private final PantryService pantryService;
    private final IngredientRepository ingredientRepository;
    private final UnitConversionService conversions;
    private final Clock clock;

    @Autowired
    public PantryImportService(PantryService pantryService, IngredientRepository ingredientRepository,
                               UnitConversionService conversions) {
        this(pantryService, ingredientRepository, conversions, Clock.systemDefaultZone());
    }

    PantryImportService(PantryService pantryService, IngredientRepository ingredientRepository,
                        UnitConversionService conversions, Clock clock) {
        this.pantryService = pantryService;
        this.ingredientRepository = ingredientRepository;
        this.conversions = conversions;
        this.clock = clock;
    }

    public byte[] template() {
        return PANTRY_WORKBOOK.template();
    }

    @Transactional(readOnly = true)
    public ImportReport preview(byte[] file, User user) {
        return plan(file, user).report(false);
    }

    @Transactional
    public ImportReport importFile(byte[] file, User user) {
        Plan plan = plan(file, user);
        if (!plan.problems.isEmpty()) {
            return plan.report(false);
        }
        plan.matcher.saveNew(ingredientRepository);
        for (PlannedItem item : plan.items) {
            item.form.setIngredientId(item.ingredient.getId());
            pantryService.create(item.form, user);
        }
        return plan.report(true);
    }

    // ------------------------------------------------------------ planning

    private record PlannedItem(PantryItemForm form, Ingredient ingredient) {
    }

    private static final class Plan {
        final Problems problems = new Problems(PANTRY_WORKBOOK);
        final List<PlannedItem> items = new ArrayList<>();
        IngredientMatcher matcher;

        ImportReport report(boolean saved) {
            if (!problems.isEmpty()) {
                return ImportReport.failed(problems.sorted());
            }
            Set<String> stocked = new LinkedHashSet<>();
            items.forEach(item -> stocked.add(item.ingredient.getName()));
            return new ImportReport(saved, List.of(), List.of(new SheetCount(PANTRY.name(), items.size())),
                    List.copyOf(stocked), matcher.plannedNames());
        }
    }

    private Plan plan(byte[] file, User user) {
        Plan plan = new Plan();
        Map<Sheet, List<ParsedRow>> rows = WorkbookFile.read(PANTRY_WORKBOOK, file, plan.problems);
        if (rows == null) {
            return plan;
        }
        List<ParsedRow> pantryRows = rows.get(PANTRY);
        if (pantryRows.isEmpty()) {
            plan.problems.file("There's nothing to import yet. Add what's on your shelves to the Pantry sheet, "
                    + "one item per row, below the headers.");
            return plan;
        }
        plan.matcher = new IngredientMatcher(user, ingredientRepository.findAllByUserOrderByNameAsc(user));
        LocalDate today = LocalDate.now(clock);

        for (ParsedRow row : pantryRows) {
            String name = row.text(INGREDIENT);
            BigDecimal amount = row.get(AMOUNT);
            Unit unit = row.get(UNIT);
            if (name == null || amount == null) {
                continue;
            }
            Ingredient ingredient = plan.matcher.find(name);
            if (ingredient == null) {
                if (unit == null) {
                    plan.problems.at(row, UNIT, "\"" + IngredientMatcher.tidy(name) + "\" isn't one of your "
                            + "ingredients yet, so give a unit for it to be kept in");
                    continue;
                }
                IngredientCategory category = row.get(CATEGORY);
                ingredient = plan.matcher.plan(name, category, unit);
            }

            BigDecimal quantity = amount;
            if (unit != null && unit != ingredient.getStockUnit()) {
                Optional<BigDecimal> converted = conversions.convert(amount, unit, ingredient.getStockUnit(),
                        ingredient);
                if (converted.isEmpty()) {
                    plan.problems.at(row, UNIT, sentence(conversions.explainFailure(unit,
                            ingredient.getStockUnit(), ingredient)) + ". Give the amount in " + ingredient.getStockUnit().getAbbreviation()
                            + ", or leave Unit blank.");
                    continue;
                }
                quantity = converted.get().setScale(3, RoundingMode.HALF_UP);
                if (quantity.compareTo(MAX_AMOUNT) > 0) {
                    plan.problems.at(row, AMOUNT, "That comes to more than " + MAX_AMOUNT.toPlainString() + " "
                            + ingredient.getStockUnit().getAbbreviation() + ", which is more than a pantry row holds");
                    continue;
                }
            }

            LocalDate purchased = row.has(PURCHASED) ? row.get(PURCHASED) : today;
            LocalDate expires = row.get(EXPIRES);
            if (expires != null && expires.isBefore(purchased)) {
                plan.problems.at(row, EXPIRES, "The expiry date falls before the purchase date");
                continue;
            }
            StorageLocation location = row.has(LOCATION) ? row.get(LOCATION) : ingredient.getDefaultLocation();

            PantryItemForm form = new PantryItemForm();
            form.setQuantity(quantity);
            form.setLocation(location);
            form.setPurchasedOn(purchased);
            form.setExpiresOn(expires);
            plan.items.add(new PlannedItem(form, ingredient));
        }
        return plan;
    }

    private static String sentence(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
