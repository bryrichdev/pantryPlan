package edu.wgu.pantryplan.web;

import edu.wgu.pantryplan.domain.GroceryList;
import edu.wgu.pantryplan.domain.GroceryListItem;
import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.security.AppUserDetails;
import edu.wgu.pantryplan.service.GroceryListService;
import edu.wgu.pantryplan.service.MealPlanService;
import edu.wgu.pantryplan.service.UnitConversionService;
import edu.wgu.pantryplan.service.UserService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
        GroceryList list = groceryListService.requireOwned(id, currentUser(principal));

        Map<Long, String> reviewReasons = new HashMap<>();
        for (GroceryListItem item : list.getItems()) {
            if (item.isNeedsReview()) {
                Ingredient ingredient = item.getIngredient();
                reviewReasons.put(item.getId(),
                        conversionService.explainFailure(item.getUnit(), ingredient.getStockUnit(), ingredient));
            }
        }

        model.addAttribute("groceryList", list);
        model.addAttribute("aisles", GroceryAisle.group(list.getItems()));
        model.addAttribute("reviewReasons", reviewReasons);
        return "grocerylists/detail";
    }

    /**
     * Ticks an item and comes back to the same spot. The fragment on the
     * redirect scrolls the browser to the row, so a long list does not jump
     * back to the top after every tick.
     */
    @PostMapping("/{id}/items/{itemId}/toggle")
    public String togglePurchased(@AuthenticationPrincipal AppUserDetails principal,
                                  @PathVariable Long id,
                                  @PathVariable Long itemId) {
        groceryListService.togglePurchased(id, itemId, currentUser(principal));
        return "redirect:/grocery-lists/" + id + "#item-" + itemId;
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
