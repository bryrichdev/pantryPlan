package edu.wgu.pantryplan.web;

import edu.wgu.pantryplan.security.AppUserDetails;
import edu.wgu.pantryplan.service.AdminService;
import edu.wgu.pantryplan.service.BulkDeleteResult;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@AuthenticationPrincipal AppUserDetails principal,
                             @PathVariable Long id,
                             RedirectAttributes redirectAttributes) {
        report(adminService.deleteUsers(List.of(id), principal.getId()), redirectAttributes);
        return "redirect:/admin/users";
    }

    @PostMapping("/users/bulk-delete")
    public String bulkDelete(@AuthenticationPrincipal AppUserDetails principal,
                             @RequestParam(name = "ids", required = false) List<Long> ids,
                             RedirectAttributes redirectAttributes) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Nothing was selected.");
            return "redirect:/admin/users";
        }
        report(adminService.deleteUsers(ids, principal.getId()), redirectAttributes);
        return "redirect:/admin/users";
    }

    /** A batch can partly succeed, so what went and what was kept are reported separately. */
    private void report(BulkDeleteResult result, RedirectAttributes redirectAttributes) {
        if (!result.deletedNothing()) {
            int count = result.getDeletedCount();
            redirectAttributes.addFlashAttribute("message",
                    "Deleted " + count + (count == 1 ? " account" : " accounts") + " and everything in them.");
        }
        if (result.hasBlocked()) {
            redirectAttributes.addFlashAttribute("error", "Kept " + String.join(", ", result.getBlockedNames())
                    + ". Admin accounts, including your own, cannot be deleted here.");
        } else if (result.deletedNothing()) {
            redirectAttributes.addFlashAttribute("error", "Those accounts no longer exist.");
        }
    }
}
