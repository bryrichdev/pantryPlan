package edu.wgu.pantryprep.web;

import edu.wgu.pantryprep.domain.PantryItem;
import edu.wgu.pantryprep.domain.StorageLocation;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.IngredientService;
import edu.wgu.pantryprep.service.PantryService;
import edu.wgu.pantryprep.service.BulkDeleteResult;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.PantryItemForm;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The pantry shelf. Add, edit, and delete happen in dialogs on the list page.
 */
@Controller
@RequestMapping("/pantry")
public class PantryController {

    private final PantryService pantryService;
    private final IngredientService ingredientService;
    private final UserService userService;

    public PantryController(PantryService pantryService,
                            IngredientService ingredientService,
                            UserService userService) {
        this.pantryService = pantryService;
        this.ingredientService = ingredientService;
        this.userService = userService;
    }

    @ModelAttribute("units")
    public Unit[] units() {
        return Unit.values();
    }

    @ModelAttribute("locations")
    public StorageLocation[] locations() {
        return StorageLocation.values();
    }

    private User currentUser(AppUserDetails principal) {
        return userService.requireById(principal.getId());
    }

    private void populateList(Model model, User user, String query, StorageLocation location) {
        List<PantryItem> results = pantryService.search(user, query, location);
        model.addAttribute("items", results);
        model.addAttribute("ingredientOptions", ingredientService.findAll(user));
        model.addAttribute("query", query == null ? "" : query);
        model.addAttribute("locationFilter", location);
        model.addAttribute("searching", (query != null && !query.isBlank()) || location != null);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("warningDays", PantryService.EXPIRY_WARNING_DAYS);
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserDetails principal,
                       @RequestParam(name = "q", required = false) String query,
                       @RequestParam(name = "location", required = false) StorageLocation location,
                       Model model) {
        populateList(model, currentUser(principal), query, location);
        if (!model.containsAttribute("pantryForm")) {
            model.addAttribute("pantryForm", new PantryItemForm());
        }
        return "pantry/list";
    }

    /**
     * Saves the dialog. The hidden filter fields are named {@code q} and
     * {@code filterLocation} rather than {@code location}, because a hidden
     * input sharing a name with a form property wins the binding and would
     * blank out whatever the cook actually chose.
     */
    @PostMapping
    public String save(@AuthenticationPrincipal AppUserDetails principal,
                       @Valid @ModelAttribute("pantryForm") PantryItemForm form,
                       BindingResult result,
                       @RequestParam(name = "q", required = false) String query,
                       @RequestParam(name = "filterLocation", required = false) StorageLocation location,
                       @RequestParam(name = "andAnother", required = false) boolean andAnother,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);

        if (form.hasBackwardsDates()) {
            result.rejectValue("expiresOn", "dates.backwards",
                    "The expiry date falls before the purchase date");
        }

        if (result.hasErrors()) {
            populateList(model, user, query, location);
            model.addAttribute("openDialog", "pantry");
            return "pantry/list";
        }

        StorageLocation savedLocation = form.getLocation();

        if (form.isNew()) {
            pantryService.create(form, user);
            redirectAttributes.addFlashAttribute("message", "Added to the pantry.");
        } else {
            pantryService.update(form.getId(), form, user);
            redirectAttributes.addFlashAttribute("message", "Pantry item updated.");
        }

        if (andAnother) {
            /* Hand the next dialog a blank form that keeps the shelf the cook is
               working through, then ask the list page to open it again. Stocking
               a fridge is usually several items in a row from the same place. */
            PantryItemForm next = new PantryItemForm();
            next.setLocation(savedLocation);
            redirectAttributes.addFlashAttribute("pantryForm", next);
            redirectAttributes.addFlashAttribute("openDialog", "pantry");
        }

        return "redirect:/pantry";
    }


    /**
     * Deletes everything ticked on the list page.
     *
     * <p>Reports the outcome in two parts, because a batch can partly succeed:
     * how many went, and separately what was kept back and why.
     */
    @PostMapping("/bulk-delete")
    public String bulkDelete(@AuthenticationPrincipal AppUserDetails principal,
                             @RequestParam(name = "ids", required = false) List<Long> ids,
                             RedirectAttributes redirectAttributes) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Nothing was selected.");
            return "redirect:/pantry";
        }

        BulkDeleteResult result = pantryService.deleteAll(ids, currentUser(principal));

        if (!result.deletedNothing()) {
            redirectAttributes.addFlashAttribute("message",
                    "Deleted " + result.getDeletedCount() + " "
                            + (result.getDeletedCount() == 1 ? "item." : "items."));
        }
        if (result.hasBlocked()) {
            redirectAttributes.addFlashAttribute("error", result.describeBlocked());
        }
        return "redirect:/pantry";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails principal,
                         @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        pantryService.delete(id, currentUser(principal));
        redirectAttributes.addFlashAttribute("message", "Removed from the pantry.");
        return "redirect:/pantry";
    }
}
