package edu.wgu.pantryplan.web;

import edu.wgu.pantryplan.domain.Recipe;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.security.AppUserDetails;
import edu.wgu.pantryplan.service.RecipeInUseException;
import edu.wgu.pantryplan.service.RecipeService;
import edu.wgu.pantryplan.service.UserService;
import edu.wgu.pantryplan.web.form.RecipeForm;
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

@Controller
@RequestMapping("/recipes")
public class RecipeController {

    private final RecipeService recipeService;
    private final UserService userService;

    public RecipeController(RecipeService recipeService, UserService userService) {
        this.recipeService = recipeService;
        this.userService = userService;
    }

    private User currentUser(AppUserDetails principal) {
        return userService.requireById(principal.getId());
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserDetails principal,
                       @RequestParam(name = "q", required = false) String name,
                       @RequestParam(name = "ingredient", required = false) String ingredient,
                       @RequestParam(name = "tag", required = false) String tag,
                       Model model) {
        User user = currentUser(principal);
        List<Recipe> results = recipeService.search(user, name, ingredient, tag);

        Map<Long, String> blockReasons = new HashMap<>();
        for (Recipe recipe : results) {
            String reason = recipeService.describeReferences(recipe);
            if (reason != null) {
                blockReasons.put(recipe.getId(), reason);
            }
        }

        boolean searching = notBlank(name) || notBlank(ingredient) || notBlank(tag);

        model.addAttribute("recipes", results);
        model.addAttribute("blockReasons", blockReasons);
        model.addAttribute("knownTags", recipeService.allTags(user));
        model.addAttribute("query", orEmpty(name));
        model.addAttribute("ingredientQuery", orEmpty(ingredient));
        model.addAttribute("tagQuery", orEmpty(tag));
        model.addAttribute("searching", searching);
        return "recipes/list";
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal AppUserDetails principal,
                         @PathVariable Long id,
                         Model model) {
        User user = currentUser(principal);
        Recipe recipe = recipeService.requireOwned(id, user);
        model.addAttribute("recipe", recipe);
        model.addAttribute("blockedReason", recipeService.describeReferences(recipe));
        return "recipes/detail";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new RecipeForm());
        return "recipes/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@AuthenticationPrincipal AppUserDetails principal,
                           @PathVariable Long id,
                           Model model) {
        Recipe recipe = recipeService.requireOwned(id, currentUser(principal));
        model.addAttribute("form", RecipeForm.from(recipe));
        return "recipes/form";
    }

    @PostMapping
    public String save(@AuthenticationPrincipal AppUserDetails principal,
                       @Valid @ModelAttribute("form") RecipeForm form,
                       BindingResult result,
                       RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);

        if (recipeService.nameCollides(user, form.getName(), form.getId())) {
            result.rejectValue("name", "name.duplicate",
                    "You already have a recipe with that name");
        }
        if (result.hasErrors()) {
            return "recipes/form";
        }

        Recipe saved = form.isNew()
                ? recipeService.create(form, user)
                : recipeService.update(form.getId(), form, user);

        redirectAttributes.addFlashAttribute("message",
                form.isNew() ? "Recipe created." : "Recipe updated.");
        return "redirect:/recipes/" + saved.getId();
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails principal,
                         @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        try {
            recipeService.delete(id, currentUser(principal));
            redirectAttributes.addFlashAttribute("message", "Recipe deleted.");
        } catch (RecipeInUseException ex) {
            redirectAttributes.addFlashAttribute("error",
                    "Could not delete " + ex.getRecipeName() + " because " + ex.getMessage() + ".");
        }
        return "redirect:/recipes";
    }
}
