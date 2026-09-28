package edu.wgu.pantryprep.web;

import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.DashboardService;
import edu.wgu.pantryprep.service.PantryService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Public landing page and the signed-in dashboard.
 */
@Controller
public class HomeController {

    /** How many rows the short lists show before linking to the full page. */
    static final int EXPIRING_ITEMS = 6;
    static final int COOKED_ITEMS = 5;

    private static final DateTimeFormatter TODAY = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US);

    private final DashboardService dashboard;

    public HomeController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/")
    public String index(@AuthenticationPrincipal AppUserDetails principal) {
        return principal == null ? "landing" : "redirect:/dashboard";
    }

    /**
     * A new account gets a checklist for getting started. Once there is
     * something to plan around, the page shows what needs using up, this
     * week's meals, and where the shopping stands.
     */
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        Long userId = principal.getId();
        LocalDate today = LocalDate.now();
        int warningDays = PantryService.EXPIRY_WARNING_DAYS;

        model.addAttribute("firstName", firstName(principal.getDisplayName()));
        model.addAttribute("today", today.format(TODAY));
        model.addAttribute("counts", dashboard.counts(userId));
        model.addAttribute("warningDays", warningDays);
        model.addAttribute("expiryCounts", dashboard.expiryCounts(userId, today, warningDays));
        model.addAttribute("expiring", dashboard.expiring(userId, today, warningDays, EXPIRING_ITEMS));
        model.addAttribute("week", dashboard.weekPlan(userId, today));
        model.addAttribute("shopping", dashboard.latestList(userId));
        model.addAttribute("cooked", dashboard.recentlyCooked(userId, COOKED_ITEMS));
        return "dashboard";
    }

    private static String firstName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        return displayName.trim().split("\\s+")[0];
    }
}
