package edu.wgu.pantryplan.web;

import edu.wgu.pantryplan.domain.GroceryList;
import edu.wgu.pantryplan.domain.GroceryListItem;
import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.StorageLocation;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.security.AppUserDetails;
import edu.wgu.pantryplan.service.GroceryListService;
import edu.wgu.pantryplan.service.MealPlanService;
import edu.wgu.pantryplan.service.UnitConversionService;
import edu.wgu.pantryplan.service.UserService;
import edu.wgu.pantryplan.web.form.StockUpForm;
import edu.wgu.pantryplan.web.form.StockUpRow;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Grocery lists: building one from a plan, reading it by aisle, and ticking
 * items off while shopping.
 */
@Controller
@RequestMapping("/grocery-lists")
public class GroceryListController {

    private final GroceryListService groceryListService;
    private final MealPlanService mealPlanService;
    private final UnitConversionService conversionService;
    private final UserService userService;

    public GroceryListController(GroceryListService groceryListService,
                                 MealPlanService mealPlanService,
                                 UnitConversionService conversionService,
                                 UserService userService) {
        this.groceryListService = groceryListService;
        this.mealPlanService = mealPlanService;
        this.conversionService = conversionService;
        this.userService = userService;
    }

    private User currentUser(AppUserDetails principal) {
        return userService.requireById(principal.getId());
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        User user = currentUser(principal);
        model.addAttribute("groceryLists", groceryListService.findAll(user));
        model.addAttribute("plans", mealPlanService.findAll(user));
        return "grocerylists/list";
    }

    /**
     * Builds, or rebuilds, a plan's list. Rebuilding replaces the old list,
     * ticks included, since the pantry is subtracted again from scratch.
     */
    @PostMapping("/generate")
    public String generate(@AuthenticationPrincipal AppUserDetails principal,
                           @RequestParam("planId") Long planId,
                           RedirectAttributes redirectAttributes) {
        GroceryList list = groceryListService.generate(planId, currentUser(principal));

        /* An empty list explains itself on the page, so the message stays short. */
        int count = list.itemCount();
        redirectAttributes.addFlashAttribute("message", count == 0
                ? "Grocery list built."
                : "Grocery list built with " + count + (count == 1 ? " item." : " items."));
        return "redirect:/grocery-lists/" + list.getId();
    }

    /**
     * The list by aisle. Flagged items get a sentence explaining why they could
     * not be checked against the pantry, worked out here so the template only
     * looks it up by item id.
     */
    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal AppUserDetails principal,
                         @PathVariable Long id,
                         Model model) {
        return renderDetail(model, groceryListService.requireOwned(id, currentUser(principal)), null);
    }

    /**
     * Puts a shop away in one go: every bought line becomes a pantry entry
     * with the amount actually bought, one purchase date, and an expiry.
     *
     * <p>Validation happens here rather than through Bean Validation, because
     * a row only needs an amount when it is ticked.
     */
    @PostMapping("/{id}/stock-up")
    public String stockUp(@AuthenticationPrincipal AppUserDetails principal,
                          @PathVariable Long id,
                          @ModelAttribute("stockUpForm") StockUpForm form,
                          BindingResult result,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        GroceryList list = groceryListService.requireOwned(id, user);

        validateStockUp(form, result);
        if (result.hasErrors()) {
            /* The dialog reopens with the rows as they were typed. */
            return renderDetail(model, list, form);
        }

        int stocked = groceryListService.stockUp(id, form, user);
        redirectAttributes.addFlashAttribute("message", stocked == 0
                ? "Nothing was ticked, so the pantry is unchanged."
                : "Added " + stocked + (stocked == 1 ? " item" : " items") + " to your pantry.");
        return "redirect:/grocery-lists/" + id;
    }

    private void validateStockUp(StockUpForm form, BindingResult result) {
        if (form.getPurchasedOn() == null) {
            result.rejectValue("purchasedOn", "purchased.required", "Enter the date you shopped");
        }
        List<StockUpRow> rows = form.getRows();
        for (int i = 0; i < rows.size(); i++) {
            StockUpRow row = rows.get(i);
            if (!row.isInclude()) {
                continue;
            }
            String field = "rows[" + i + "].";
            BigDecimal quantity = row.getQuantity();
            if (quantity == null) {
                result.rejectValue(field + "quantity", "quantity.required", "Enter how much you bought");
            } else if (quantity.signum() <= 0) {
                result.rejectValue(field + "quantity", "quantity.positive", "Amount must be more than zero");
            }
            if (row.getLocation() == null) {
                result.rejectValue(field + "location", "location.required", "Choose where it goes");
            }
            if (form.getPurchasedOn() != null && row.getExpiresOn() != null
                    && row.getExpiresOn().isBefore(form.getPurchasedOn())) {
                result.rejectValue(field + "expiresOn", "expiry.backwards",
                        "That is before the shopping date");
            }
        }
    }

    /**
     * The detail page. Flagged items get a sentence explaining why they could
     * not be checked against the pantry, worked out here so the template only
     * looks it up by item id.
     *
     * <p>Everything the page needs is gathered here rather than in the GET
     * method, because the stock-up form reaches this view too when it is
     * rejected.
     *
     * @param submitted the rejected form to show again, or null for a fresh one
     */
    private String renderDetail(Model model, GroceryList list, StockUpForm submitted) {
        Map<Long, String> reviewReasons = new HashMap<>();
        for (GroceryListItem item : list.getItems()) {
            if (item.isNeedsReview()) {
                Ingredient ingredient = item.getIngredient();
                reviewReasons.put(item.getId(),
                        conversionService.explainFailure(item.getUnit(), ingredient.getStockUnit(), ingredient));
            }
        }

        List<GroceryListItem> stockable = groceryListService.stockable(list);
        model.addAttribute("groceryList", list);
        model.addAttribute("aisles", GroceryAisle.group(list.getItems()));
        model.addAttribute("reviewReasons", reviewReasons);
        model.addAttribute("stockableItems", stockable);
        model.addAttribute("readyCount", stockable.stream().filter(GroceryListItem::isPurchased).count());
        model.addAttribute("locations", StorageLocation.values());

        if (submitted == null) {
            model.addAttribute("stockUpForm", blankStockUpForm(stockable));
        } else {
            model.addAttribute("openDialog", "stockup-dialog");
        }
        return "grocerylists/detail";
    }

    /**
     * Prefills a row per line that could be put away.
     *
     * <p>A row is included only when its line is ticked. The browser flips that
     * as items are ticked, so the dialog never has to be fetched again.
     *
     * <p>The amount offered is the ingredient's usual amount when it has one,
     * because shopping happens in packages: a five pound bag covers a list
     * asking for half a pound. Failing that it is what the list asked for,
     * but only when that figure is in the stocking unit. A flagged line is in
     * the recipe's unit, which cannot be compared with the shelf, so it is
     * left blank for the cook to fill in.
     */
    private StockUpForm blankStockUpForm(List<GroceryListItem> items) {
        StockUpForm form = new StockUpForm();
        List<StockUpRow> rows = new ArrayList<>();
        for (GroceryListItem item : items) {
            Ingredient ingredient = item.getIngredient();
            StockUpRow row = new StockUpRow();
            row.setItemId(item.getId());
            row.setInclude(item.isPurchased());
            if (ingredient.getDefaultQuantity() != null) {
                row.setQuantity(ingredient.getDefaultQuantity());
            } else if (!item.isNeedsReview()) {
                row.setQuantity(item.getNeededQuantity());
            }
            row.setLocation(ingredient.getDefaultLocation());
            rows.add(row);
        }
        form.setRows(rows);
        return form;
    }

    /**
     * A plain, printable version of the list.
     *
     * <p>Only what is still to buy is shown. Anything already ticked has either
     * been bought or turned out to be on the shelf, so it would only be clutter
     * on paper. The page says how many were left off, so a short sheet is not
     * mistaken for a missing one.
     */
    @GetMapping("/{id}/print")
    public String print(@AuthenticationPrincipal AppUserDetails principal,
                        @PathVariable Long id,
                        Model model) {
        GroceryList list = groceryListService.requireOwned(id, currentUser(principal));
        List<GroceryListItem> stillToBuy = list.getItems().stream()
                .filter(item -> !item.isPurchased())
                .toList();

        model.addAttribute("groceryList", list);
        model.addAttribute("aisles", GroceryAisle.group(stillToBuy));
        model.addAttribute("hiddenCount", list.purchasedCount());
        return "grocerylists/print";
    }

    /**
     * Ticks an item off, or back on.
     *
     * <p>Two answers from one route. app.js sends the header and gets an empty
     * 204, because it updates the page itself and would only throw away a
     * rendered one. Without JavaScript the browser posts the form normally and
     * follows the redirect back to the list. No fragment on that redirect: it
     * would scroll the ticked row up to the top of the window, which is its own
     * kind of jump.
     */
    @PostMapping("/{id}/items/{itemId}/toggle")
    public Object togglePurchased(@AuthenticationPrincipal AppUserDetails principal,
                                  @PathVariable Long id,
                                  @PathVariable Long itemId,
                                  @RequestHeader(name = "X-Requested-With", required = false) String requestedWith) {
        groceryListService.togglePurchased(id, itemId, currentUser(principal));
        if ("fetch".equals(requestedWith)) {
            return ResponseEntity.noContent().build();
        }
        return "redirect:/grocery-lists/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails principal,
                         @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        groceryListService.delete(id, currentUser(principal));
        redirectAttributes.addFlashAttribute("message", "Grocery list deleted.");
        return "redirect:/grocery-lists";
    }
}
