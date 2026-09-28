package edu.wgu.pantryprep.web;

import edu.wgu.pantryprep.domain.MealPlan;
import edu.wgu.pantryprep.domain.MealSlot;
import edu.wgu.pantryprep.domain.PlanEntry;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.BulkDeleteResult;
import edu.wgu.pantryprep.service.CookResult;
import edu.wgu.pantryprep.service.CookUndoResult;
import edu.wgu.pantryprep.service.GroceryListService;
import edu.wgu.pantryprep.service.MealPlanService;
import edu.wgu.pantryprep.service.RecipeService;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.AutoFillForm;
import edu.wgu.pantryprep.web.form.MealPlanForm;
import edu.wgu.pantryprep.web.form.PlanEntryForm;
import jakarta.validation.Valid;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/meal-plans")
public class MealPlanController {

    private final MealPlanService mealPlanService;
    private final RecipeService recipeService;
    private final GroceryListService groceryListService;
    private final UserService userService;

    public MealPlanController(MealPlanService mealPlanService,
                              RecipeService recipeService,
                              GroceryListService groceryListService,
                              UserService userService) {
        this.mealPlanService = mealPlanService;
        this.recipeService = recipeService;
        this.groceryListService = groceryListService;
        this.userService = userService;
    }

    @ModelAttribute("slots")
    public MealSlot[] slots() {
        return MealSlot.values();
    }

    private User currentUser(AppUserDetails principal) {
        return userService.requireById(principal.getId());
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        User user = currentUser(principal);
        model.addAttribute("plans", mealPlanService.findAll(user));
        model.addAttribute("today", LocalDate.now());
        if (!model.containsAttribute("planForm")) {
            MealPlanForm form = new MealPlanForm();
            /* Default to the coming Monday, which is what most people mean by
               "next week" when they sit down to plan. */
            LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            form.setWeekStartDate(weekStart);
            form.setName(defaultPlanName(weekStart));
            model.addAttribute("planForm", form);
        }
        return "mealplans/list";
    }

    private String defaultPlanName(LocalDate weekStart) {
        return String.format("%02d/%02d Meal Plan", weekStart.getMonthValue(), weekStart.getDayOfMonth());
    }

    @PostMapping
    public String savePlan(@AuthenticationPrincipal AppUserDetails principal,
                           @Valid @ModelAttribute("planForm") MealPlanForm form,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);

        if (result.hasErrors()) {
            model.addAttribute("plans", mealPlanService.findAll(user));
            model.addAttribute("today", LocalDate.now());
            model.addAttribute("openDialog", "plan");
            return "mealplans/list";
        }

        if (form.isNew()) {
            MealPlan created = mealPlanService.create(form, user);
            redirectAttributes.addFlashAttribute("message", "Plan created.");
            return "redirect:/meal-plans/" + created.getId();
        }

        mealPlanService.update(form.getId(), form, user);
        redirectAttributes.addFlashAttribute("message", "Plan updated.");
        return "redirect:/meal-plans/" + form.getId();
    }

    /**
     * The week builder. Days are assembled here rather than filtered in the
     * template, so the view just walks a prepared list.
     */
    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal AppUserDetails principal,
                         @PathVariable Long id,
                         Model model) {
        User user = currentUser(principal);
        MealPlan plan = mealPlanService.requireOwned(id, user);
        List<PlanEntry> ordered = mealPlanService.entriesInOrder(plan);

        List<PlannedDay> days = new ArrayList<>();
        for (LocalDate date : mealPlanService.weekDates(plan)) {
            List<PlanEntry> onDay = ordered.stream()
                    .filter(entry -> date.equals(entry.getPlanDate()))
                    .toList();
            days.add(new PlannedDay(date, onDay));
        }

        model.addAttribute("plan", plan);
        model.addAttribute("days", days);
        model.addAttribute("entryCount", ordered.size());
        model.addAttribute("recipeOptions", recipeService.findAll(user));
        model.addAttribute("groceryList", groceryListService.findForPlan(plan).orElse(null));
        model.addAttribute("today", LocalDate.now());
        if (!model.containsAttribute("entryForm")) {
            PlanEntryForm form = new PlanEntryForm();
            form.setPlanDate(plan.getWeekStartDate());
            model.addAttribute("entryForm", form);
        }
        if (!model.containsAttribute("planForm")) {
            model.addAttribute("planForm", MealPlanForm.from(plan));
        }
        if (!model.containsAttribute("autoFillForm")) {
            model.addAttribute("autoFillForm", new AutoFillForm());
        }
        return "mealplans/detail";
    }

    @PostMapping("/{id}/entries")
    public String addEntry(@AuthenticationPrincipal AppUserDetails principal,
                           @PathVariable Long id,
                           @Valid @ModelAttribute("entryForm") PlanEntryForm form,
                           BindingResult result,
                           RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        MealPlan plan = mealPlanService.requireOwned(id, user);

        if (!result.hasErrors() && !mealPlanService.dateIsInWeek(plan, form.getPlanDate())) {
            result.rejectValue("planDate", "date.outsideWeek",
                    "That day is not part of this plan's week");
        }

        if (result.hasErrors()) {
            /* Carry the rejected entry and its messages through the redirect so
               the dialog reopens with them intact. */
            redirectAttributes.addFlashAttribute(
                    "org.springframework.validation.BindingResult.entryForm", result);
            redirectAttributes.addFlashAttribute("entryForm", form);
            redirectAttributes.addFlashAttribute("openDialog", "entry");
            return "redirect:/meal-plans/" + id;
        }

        mealPlanService.addEntry(id, form, user);
        redirectAttributes.addFlashAttribute("message", "Meal added to the plan.");
        return "redirect:/meal-plans/" + id;
    }


    /**
     * Fills the week automatically. Reports how many meals landed, and says so
     * plainly when nothing did, since "no recipes yet" and "every slot was
     * already full" both look like nothing happening.
     */
    @PostMapping("/{id}/auto-fill")
    public String autoFill(@AuthenticationPrincipal AppUserDetails principal,
                           @PathVariable Long id,
                           @Valid @ModelAttribute("autoFillForm") AutoFillForm form,
                           BindingResult result,
                           RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);

        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "org.springframework.validation.BindingResult.autoFillForm", result);
            redirectAttributes.addFlashAttribute("autoFillForm", form);
            redirectAttributes.addFlashAttribute("openDialog", "autofill");
            return "redirect:/meal-plans/" + id;
        }

        int added = mealPlanService.autoFill(id, form, user);

        if (added == 0) {
            redirectAttributes.addFlashAttribute("error",
                    "Nothing was added. Either you have no recipes yet, or every meal "
                            + "you chose is already scheduled.");
        } else {
            redirectAttributes.addFlashAttribute("message",
                    "Filled " + added + (added == 1 ? " meal." : " meals."));
        }
        return "redirect:/meal-plans/" + id;
    }

    @PostMapping("/{id}/entries/{entryId}/delete")
    public String removeEntry(@AuthenticationPrincipal AppUserDetails principal,
                              @PathVariable Long id,
                              @PathVariable Long entryId,
                              RedirectAttributes redirectAttributes) {
        mealPlanService.removeEntry(id, entryId, currentUser(principal));
        redirectAttributes.addFlashAttribute("message", "Meal removed from the plan.");
        return "redirect:/meal-plans/" + id;
    }

    @PostMapping("/{id}/entries/bulk-delete")
    public String removeEntries(@AuthenticationPrincipal AppUserDetails principal,
                                @PathVariable Long id,
                                @org.springframework.web.bind.annotation.RequestParam(required = false)
                                Collection<Long> ids,
                                RedirectAttributes redirectAttributes) {
        BulkDeleteResult result = mealPlanService.removeEntries(id, ids, currentUser(principal));
        if (result.getDeletedCount() == 0) {
            redirectAttributes.addFlashAttribute("error", "No meals were removed.");
        } else {
            redirectAttributes.addFlashAttribute("message", "Removed " + result.getDeletedCount()
                    + (result.getDeletedCount() == 1 ? " meal." : " meals."));
        }
        return "redirect:/meal-plans/" + id;
    }

    @PostMapping("/{id}/entries/bulk-cook")
    public String markEntriesCooked(@AuthenticationPrincipal AppUserDetails principal,
                                    @PathVariable Long id,
                                    @org.springframework.web.bind.annotation.RequestParam(required = false)
                                    Collection<Long> ids,
                                    RedirectAttributes redirectAttributes) {
        CookResult result = mealPlanService.markEntriesCooked(id, ids, currentUser(principal));
        addCookFlash(result, redirectAttributes);
        return "redirect:/meal-plans/" + id;
    }

    @PostMapping("/{id}/entries/bulk-uncook")
    public String markEntriesNotCooked(@AuthenticationPrincipal AppUserDetails principal,
                                       @PathVariable Long id,
                                       @org.springframework.web.bind.annotation.RequestParam(required = false)
                                       Collection<Long> ids,
                                       RedirectAttributes redirectAttributes) {
        CookUndoResult result = mealPlanService.markEntriesNotCooked(id, ids, currentUser(principal));
        addUndoCookFlash(result, redirectAttributes);
        return "redirect:/meal-plans/" + id;
    }

    @PostMapping("/{id}/entries/{entryId}/cook")
    public String markEntryCooked(@AuthenticationPrincipal AppUserDetails principal,
                                  @PathVariable Long id,
                                  @PathVariable Long entryId,
                                  RedirectAttributes redirectAttributes) {
        CookResult result = mealPlanService.markEntryCooked(id, entryId, currentUser(principal));
        addCookFlash(result, redirectAttributes);
        return "redirect:/meal-plans/" + id;
    }

    @PostMapping("/{id}/entries/{entryId}/uncook")
    public String markEntryNotCooked(@AuthenticationPrincipal AppUserDetails principal,
                                     @PathVariable Long id,
                                     @PathVariable Long entryId,
                                     RedirectAttributes redirectAttributes) {
        CookUndoResult result = mealPlanService.markEntryNotCooked(id, entryId, currentUser(principal));
        addUndoCookFlash(result, redirectAttributes);
        return "redirect:/meal-plans/" + id;
    }

    private void addCookFlash(CookResult result, RedirectAttributes redirectAttributes) {
        if (result.getCookedCount() == 0) {
            redirectAttributes.addFlashAttribute("error", "Those meals were already marked as cooked.");
            return;
        }

        String message = "Marked " + result.getCookedCount()
                + (result.getCookedCount() == 1 ? " meal as cooked." : " meals as cooked.");
        if (result.getUnconvertibleLineCount() > 0) {
            message += " " + result.getUnconvertibleLineCount()
                    + (result.getUnconvertibleLineCount() == 1
                    ? " ingredient amount could not be converted to its pantry unit."
                    : " ingredient amounts could not be converted to their pantry units.");
        }
        redirectAttributes.addFlashAttribute("message", message);
    }

    private void addUndoCookFlash(CookUndoResult result, RedirectAttributes redirectAttributes) {
        if (result.getUncookedCount() > 0) {
            redirectAttributes.addFlashAttribute("message", "Reversed cooking for "
                    + result.getUncookedCount()
                    + (result.getUncookedCount() == 1 ? " meal." : " meals."));
        } else if (result.getUnrestorableLogCount() == 0) {
            redirectAttributes.addFlashAttribute("error", "No cooked meals were found in that selection.");
        }
        if (result.getUnrestorableLogCount() > 0) {
            redirectAttributes.addFlashAttribute("error", "Cooking could not be reversed because a pantry "
                    + "unit changed and the recorded amount cannot be converted safely.");
        }
    }

    @PostMapping("/{id}/delete")
    public String deletePlan(@AuthenticationPrincipal AppUserDetails principal,
                             @PathVariable Long id,
                             RedirectAttributes redirectAttributes) {
        mealPlanService.delete(id, currentUser(principal));
        redirectAttributes.addFlashAttribute("message", "Plan deleted.");
        return "redirect:/meal-plans";
    }
}
