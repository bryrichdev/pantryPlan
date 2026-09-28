package edu.wgu.pantryprep.web;

import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.IngredientCategory;
import edu.wgu.pantryprep.domain.StorageLocation;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.IngredientInUseException;
import edu.wgu.pantryprep.service.IngredientService;
import edu.wgu.pantryprep.service.BulkDeleteResult;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.IngredientForm;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Ingredient catalogue. Add, edit, and delete happen in dialogs, which can be
 * opened either from the ingredient list or from a recipe that uses them.
 */
@Controller
@RequestMapping("/ingredients")
public class IngredientController {

    private static final String BINDING_RESULT_KEY =
            "org.springframework.validation.BindingResult.ingredientForm";

    private final IngredientService ingredientService;
    private final UserService userService;

    public IngredientController(IngredientService ingredientService, UserService userService) {
        this.ingredientService = ingredientService;
        this.userService = userService;
    }

    @ModelAttribute("categories")
    public IngredientCategory[] categories() {
        return IngredientCategory.values();
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

    /**
     * Accepts a redirect target only when it is a path on this site.
     *
     * <p>The value arrives in a form field, so a caller could put anything in
     * it. Anything absolute, protocol-relative, or backslash-escaped is thrown
     * away, which keeps this from becoming an open redirect that bounces a
     * signed-in cook to somebody else's page.
     */
    private String safeReturnTo(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String trimmed = candidate.trim();
        if (!trimmed.startsWith("/") || trimmed.startsWith("//") || trimmed.contains("\\")) {
            return null;
        }
        return trimmed;
    }

    /**
     * Loads everything the list page needs. Called both for a plain page view
     * and when a rejected form has to be redisplayed with its errors.
     */
    private void populateList(Model model, User user, String query) {
        List<Ingredient> results = ingredientService.search(user, query);
        Map<Long, String> blockReasons = new HashMap<>();
        for (Ingredient ingredient : results) {
            String reason = ingredientService.describeReferences(ingredient);
            if (reason != null) {
                blockReasons.put(ingredient.getId(), reason);
            }
        }
        model.addAttribute("ingredients", results);
        model.addAttribute("blockReasons", blockReasons);
        model.addAttribute("query", query == null ? "" : query);
        model.addAttribute("searching", query != null && !query.isBlank());
        model.addAttribute("presetsAvailable", ingredientService.countMissingPresets(user));
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserDetails principal,
                       @RequestParam(name = "q", required = false) String query,
                       Model model) {
        populateList(model, currentUser(principal), query);
        if (!model.containsAttribute("ingredientForm")) {
            model.addAttribute("ingredientForm", new IngredientForm());
        }
        return "ingredients/list";
    }

    @PostMapping
    public String save(@AuthenticationPrincipal AppUserDetails principal,
                       @Valid @ModelAttribute("ingredientForm") IngredientForm form,
                       BindingResult result,
                       @RequestParam(name = "q", required = false) String query,
                       @RequestParam(name = "returnTo", required = false) String returnTo,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        String destination = safeReturnTo(returnTo);

        if (ingredientService.nameCollides(user, form.getName(), form.getId())) {
            result.rejectValue("name", "name.duplicate",
                    "You already have an ingredient with that name");
        }

        if (result.hasErrors()) {
            if (destination != null) {
                /* Carry the rejected form and its errors across the redirect so
                   the dialog can reopen on the page the cook started from with
                   their typing and the messages both intact. */
                redirectAttributes.addFlashAttribute(BINDING_RESULT_KEY, result);
                redirectAttributes.addFlashAttribute("ingredientForm", form);
                redirectAttributes.addFlashAttribute("openDialog", "ingredient");
                return "redirect:" + destination;
            }
            populateList(model, user, query);
            model.addAttribute("openDialog", "ingredient");
            return "ingredients/list";
        }

        if (form.isNew()) {
            ingredientService.create(form, user);
            redirectAttributes.addFlashAttribute("message", "Ingredient added.");
        } else {
            ingredientService.update(form.getId(), form, user);
            redirectAttributes.addFlashAttribute("message", "Ingredient updated.");
        }
        return "redirect:" + (destination != null ? destination : "/ingredients");
    }


    /**
     * Copies the shared starter catalogue into this account.
     */
    @PostMapping("/import-presets")
    public String importPresets(@AuthenticationPrincipal AppUserDetails principal,
                                RedirectAttributes redirectAttributes) {
        int added = ingredientService.importPresets(currentUser(principal));
        if (added == 0) {
            redirectAttributes.addFlashAttribute("message",
                    "You already have every starter ingredient.");
        } else {
            redirectAttributes.addFlashAttribute("message",
                    "Added " + added + " starter " + (added == 1 ? "ingredient." : "ingredients."));
        }
        return "redirect:/ingredients";
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
            return "redirect:/ingredients";
        }

        BulkDeleteResult result = ingredientService.deleteAll(ids, currentUser(principal));

        if (!result.deletedNothing()) {
            redirectAttributes.addFlashAttribute("message",
                    "Deleted " + result.getDeletedCount() + " "
                            + (result.getDeletedCount() == 1 ? "ingredient." : "ingredients."));
        }
        if (result.hasBlocked()) {
            redirectAttributes.addFlashAttribute("error", result.describeBlocked());
        }
        return "redirect:/ingredients";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails principal,
                         @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        try {
            ingredientService.delete(id, currentUser(principal));
            redirectAttributes.addFlashAttribute("message", "Ingredient deleted.");
        } catch (IngredientInUseException ex) {
            redirectAttributes.addFlashAttribute("error",
                    "Could not delete " + ex.getIngredientName() + " because " + ex.getMessage() + ".");
        }
        return "redirect:/ingredients";
    }
}
