package edu.wgu.pantryplan.web;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.IngredientCategory;
import edu.wgu.pantryplan.domain.Recipe;
import edu.wgu.pantryplan.domain.StorageLocation;
import edu.wgu.pantryplan.domain.Unit;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.security.AppUserDetails;
import edu.wgu.pantryplan.service.IngredientService;
import edu.wgu.pantryplan.service.RecipeInUseException;
import edu.wgu.pantryplan.service.RecipeService;
import edu.wgu.pantryplan.service.UserService;
import edu.wgu.pantryplan.web.form.IngredientForm;
import edu.wgu.pantryplan.web.form.RecipeForm;
import edu.wgu.pantryplan.web.form.RecipeLineForm;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final BigDecimal MIN_QUANTITY = new BigDecimal("0.001");
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("9999999.999");

    private final RecipeService recipeService;
    private final IngredientService ingredientService;
    private final UserService userService;

    public RecipeController(RecipeService recipeService,
                            IngredientService ingredientService,
                            UserService userService) {
        this.recipeService = recipeService;
        this.ingredientService = ingredientService;
        this.userService = userService;
    }

    @ModelAttribute("units")
    public Unit[] units() {
        return Unit.values();
    }

    @ModelAttribute("categories")
    public IngredientCategory[] categories() {
        return IngredientCategory.values();
    }

    @ModelAttribute("locations")
    public StorageLocation[] locations() {
        return StorageLocation.values();
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

        model.addAttribute("recipes", results);
        model.addAttribute("blockReasons", blockReasons);
        model.addAttribute("knownTags", recipeService.allTags(user));
        model.addAttribute("query", orEmpty(name));
        model.addAttribute("ingredientQuery", orEmpty(ingredient));
        model.addAttribute("tagQuery", orEmpty(tag));
        model.addAttribute("searching", notBlank(name) || notBlank(ingredient) || notBlank(tag));
        return "recipes/list";
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Recipe detail. Carries the shared ingredient dialog so a density or
     * category can be corrected without leaving the page, and tells that dialog
     * to come back here after saving.
     */
    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal AppUserDetails principal,
                         @PathVariable Long id,
                         Model model) {
        Recipe recipe = recipeService.requireOwned(id, currentUser(principal));
        model.addAttribute("recipe", recipe);
        model.addAttribute("blockedReason", recipeService.describeReferences(recipe));
        model.addAttribute("returnTo", "/recipes/" + id);
        if (!model.containsAttribute("ingredientForm")) {
            model.addAttribute("ingredientForm", new IngredientForm());
        }
        return "recipes/detail";
    }

    @GetMapping("/new")
    public String createForm(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        RecipeForm form = new RecipeForm();
        form.ensureOneEmptyRow();
        model.addAttribute("form", form);
        model.addAttribute("ingredientOptions", ingredientService.findAll(currentUser(principal)));
        return "recipes/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@AuthenticationPrincipal AppUserDetails principal,
                           @PathVariable Long id,
                           Model model) {
        User user = currentUser(principal);
        Recipe recipe = recipeService.requireOwned(id, user);
        model.addAttribute("form", RecipeForm.from(recipe));
        model.addAttribute("ingredientOptions", ingredientService.findAll(user));
        return "recipes/form";
    }

    @PostMapping
    public String save(@AuthenticationPrincipal AppUserDetails principal,
                       @Valid @ModelAttribute("form") RecipeForm form,
                       BindingResult result,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);

        form.removeBlankLines();
        validateLines(form, result, user);

        if (recipeService.nameCollides(user, form.getName(), form.getId())) {
            result.rejectValue("name", "name.duplicate",
                    "You already have a recipe with that name");
        }

        if (result.hasErrors()) {
            form.ensureOneEmptyRow();
            model.addAttribute("ingredientOptions", ingredientService.findAll(user));
            return "recipes/form";
        }

        Recipe saved = form.isNew()
                ? recipeService.create(form, user)
                : recipeService.update(form.getId(), form, user);

        redirectAttributes.addFlashAttribute("message",
                form.isNew() ? "Recipe created." : "Recipe updated.");
        return "redirect:/recipes/" + saved.getId();
    }

    /**
     * Checks the ingredient rows by hand rather than with cascading bean
     * validation, because a row the cook never touched has already been
     * discarded by this point and must not raise errors. Rows that survive are
     * required to be complete, and the ingredient must belong to this account.
     */
    private void validateLines(RecipeForm form, BindingResult result, User user) {
        Set<Long> ownedIds = new HashSet<>();
        for (Ingredient ingredient : ingredientService.findAll(user)) {
            ownedIds.add(ingredient.getId());
        }

        List<RecipeLineForm> lines = form.getLines();
        for (int i = 0; i < lines.size(); i++) {
            RecipeLineForm line = lines.get(i);
            String prefix = "lines[" + i + "].";

            if (line.getIngredientId() == null) {
                result.rejectValue(prefix + "ingredientId", "line.ingredient.required",
                        "Choose an ingredient");
            } else if (!ownedIds.contains(line.getIngredientId())) {
                result.rejectValue(prefix + "ingredientId", "line.ingredient.unknown",
                        "That ingredient is not on your list");
            }

            if (line.getQuantity() == null) {
                result.rejectValue(prefix + "quantity", "line.quantity.required",
                        "Enter an amount");
            } else if (line.getQuantity().compareTo(MIN_QUANTITY) < 0) {
                result.rejectValue(prefix + "quantity", "line.quantity.min",
                        "Amount must be greater than zero");
            } else if (line.getQuantity().compareTo(MAX_QUANTITY) > 0) {
                result.rejectValue(prefix + "quantity", "line.quantity.max",
                        "That amount is too large");
            }

            if (line.getUnit() == null) {
                result.rejectValue(prefix + "unit", "line.unit.required", "Choose a unit");
            }

            if (line.getNote() != null && line.getNote().length() > 255) {
                result.rejectValue(prefix + "note", "line.note.length",
                        "Note must be 255 characters or fewer");
            }
        }
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
