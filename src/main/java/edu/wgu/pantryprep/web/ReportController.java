package edu.wgu.pantryprep.web;

import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.report.Report;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.ReportService;
import edu.wgu.pantryprep.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Account reports share one table template while retaining report-specific rows. */
@Controller
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;
    private final UserService userService;

    public ReportController(ReportService reportService, UserService userService) {
        this.reportService = reportService;
        this.userService = userService;
    }

    @GetMapping
    public String list() {
        return "reports/list";
    }

    @GetMapping("/pantry-stock")
    public String pantryStock(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        return detail(reportService.pantryStock(currentUser(principal)), model);
    }

    @GetMapping("/recipe-usage")
    public String recipeUsage(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        return detail(reportService.recipeUsage(currentUser(principal)), model);
    }

    @GetMapping("/near-expired-pantry-items")
    public String nearExpiredPantryItems(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        return detail(reportService.nearExpiredPantryItems(currentUser(principal)), model);
    }

    @GetMapping("/expired-pantry-items")
    public String expiredPantryItems(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        return detail(reportService.expiredPantryItems(currentUser(principal)), model);
    }

    private String detail(Report report, Model model) {
        model.addAttribute("report", report);
        return "reports/detail";
    }

    private User currentUser(AppUserDetails principal) {
        return userService.requireById(principal.getId());
    }
}
