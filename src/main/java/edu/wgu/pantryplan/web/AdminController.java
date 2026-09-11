package edu.wgu.pantryplan.web;

import edu.wgu.pantryplan.security.AppUserDetails;
import edu.wgu.pantryplan.service.AdminService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The admin tab. Starting and ending "view as" is not handled here: those
 * POSTs are caught by SwitchUserFilter before they reach any controller.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public String users(@AuthenticationPrincipal AppUserDetails principal,
                        @RequestParam(name = "switchFailed", required = false) String switchFailed,
                        Model model) {
        model.addAttribute("users", adminService.listUsers());
        model.addAttribute("currentUserId", principal.getId());
        if (switchFailed != null) {
            model.addAttribute("error",
                    "That account could not be opened. It may be disabled, removed, or another admin.");
        }
        return "admin/users";
    }
}
