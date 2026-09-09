package edu.wgu.pantryplan.web;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.IngredientCategory;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.security.AppUserDetails;
import edu.wgu.pantryplan.service.IngredientInUseException;
import edu.wgu.pantryplan.service.IngredientService;
import edu.wgu.pantryplan.service.UserService;
import edu.wgu.pantryplan.web.form.IngredientForm;
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
 * Ingredient catalogue. Add, edit, and delete all happen in dialogs on the list
 * page, so every route here either renders that page or redirects back to it.
 */
@Controller
@RequestMapping("/ingredients")
public class IngredientController {

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

    private User currentUser(AppUserDetails principal) {
        return userService.requireById(principal.getId());
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
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserDetails principal,
                       @RequestParam(name = "q", required = false) String query,
                       Model model) {
        populateList(model, currentUser(principal), query);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new IngredientForm());
        }
        return "ingredients/list";
    }

    @PostMapping
    public String save(@AuthenticationPrincipal AppUserDetails principal,
                       @Valid @ModelAttribute("form") IngredientForm form,
                       BindingResult result,
                       @RequestParam(name = "q", required = false) String query,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);

        if (ingredientService.nameCollides(user, form.getName(), form.getId())) {
            result.rejectValue("name", "name.duplicate",
                    "You already have an ingredient with that name");
        }

        if (result.hasErrors()) {
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
