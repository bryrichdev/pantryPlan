package edu.wgu.pantryprep.web;

import edu.wgu.pantryprep.security.AppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Public landing page and the signed-in dashboard.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String index(@AuthenticationPrincipal AppUserDetails principal) {
        return principal == null ? "landing" : "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        model.addAttribute("displayName", principal.getDisplayName());
        return "dashboard";
    }
}
