package edu.wgu.pantryprep.web;

import edu.wgu.pantryprep.audit.AuditLogService;
import edu.wgu.pantryprep.domain.FeedbackStatus;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.AdminService;
import edu.wgu.pantryprep.service.BulkDeleteResult;
import edu.wgu.pantryprep.service.FeedbackService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The admin tab. Starting and ending "view as" is not handled here: those
 * POSTs are caught by SwitchUserFilter before they reach any controller.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final AuditLogService auditLogService;
    private final FeedbackService feedbackService;
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    public AdminController(AdminService adminService, AuditLogService auditLogService,
                           FeedbackService feedbackService) {
        this.adminService = adminService;
        this.auditLogService = auditLogService;
        this.feedbackService = feedbackService;
    }

    /**
     * Problem reports and change requests, newest first. Open ones by default,
     * since those are the ones still waiting on someone.
     */
    @GetMapping("/feedback")
    public String feedback(@RequestParam(name = "show", defaultValue = "open") String show, Model model) {
        String filter = feedbackFilter(show);
        FeedbackStatus status = switch (filter) {
            case "resolved" -> FeedbackStatus.RESOLVED;
            case "all" -> null;
            default -> FeedbackStatus.OPEN;
        };
        model.addAttribute("reports", feedbackService.list(status));
        model.addAttribute("openCount", feedbackService.countOpen());
        model.addAttribute("show", filter);
        return "admin/feedback";
    }

    @PostMapping("/feedback/{id}/status")
    public String setFeedbackStatus(@PathVariable Long id,
                                    @RequestParam(name = "status") FeedbackStatus status,
                                    @RequestParam(name = "show", defaultValue = "open") String show,
                                    RedirectAttributes redirectAttributes) {
        feedbackService.setStatus(id, status);
        redirectAttributes.addFlashAttribute("message",
                status == FeedbackStatus.RESOLVED ? "Marked resolved." : "Reopened.");
        return "redirect:/admin/feedback?show=" + feedbackFilter(show);
    }

    private static String feedbackFilter(String show) {
        return "resolved".equals(show) || "all".equals(show) ? show : "open";
    }

    /**
     * The audit log, newest first, in pages of a chosen size. Filters arrive as query
     * parameters so a filtered view can be bookmarked or shared.
     */
    @GetMapping("/logs")
    public String logs(@RequestParam(name = "table", required = false) String table,
                       @RequestParam(name = "action", required = false) String action,
                       @RequestParam(name = "account", required = false) String account,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       @RequestParam(name = "size", defaultValue = "50") int size,
                       Model model) {
        model.addAttribute("log", auditLogService.search(table, action, account, page, size));
        model.addAttribute("tables", AuditLogService.TABLES);
        model.addAttribute("actions", AuditLogService.ACTIONS);
        model.addAttribute("pageSizes", AuditLogService.PAGE_SIZES);
        return "admin/logs";
    }

    /**
     * Empties the log. Nothing records this in the log itself — the log is
     * gone — so the admin who did it and the size of what went are written to
     * the application log instead.
     */
    @PostMapping("/logs/clear")
    public String clearLogs(@AuthenticationPrincipal AppUserDetails principal,
                            RedirectAttributes redirectAttributes) {
        int cleared = auditLogService.clear();
        if (cleared == 0) {
            redirectAttributes.addFlashAttribute("error", "There was nothing to clear.");
        } else {
            log.info("Admin {} cleared the audit log, {} entries", principal.getEmail(), cleared);
            redirectAttributes.addFlashAttribute("message",
                    "Cleared " + cleared + (cleared == 1 ? " entry." : " entries."));
        }
        return "redirect:/admin/logs";
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
