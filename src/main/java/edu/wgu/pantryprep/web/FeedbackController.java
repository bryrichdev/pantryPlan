package edu.wgu.pantryprep.web;

import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.FeedbackService;
import edu.wgu.pantryprep.web.form.FeedbackForm;
import jakarta.validation.Valid;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Receives the report dialog from the corner button.
 *
 * <p>The dialog normally posts in the background, so a half-filled form on the
 * page underneath is never lost. That request gets 204 on success, or 400 with
 * the first problem as plain text. Without JavaScript the form posts normally
 * and comes back to the page it was sent from.
 */
@Controller
public class FeedbackController {

    /* Plain site paths only. This rules out other sites, protocol-relative
       URLs, and anything a redirect would try to expand or escape. */
    private static final Pattern SITE_PATH = Pattern.compile("^/[A-Za-z0-9/_.-]*$");

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping("/feedback")
    public Object submit(@AuthenticationPrincipal AppUserDetails principal,
                         @Valid @ModelAttribute("feedbackForm") FeedbackForm form,
                         BindingResult result,
                         @RequestHeader(name = "X-Requested-With", required = false) String requestedWith,
                         RedirectAttributes redirectAttributes) {
        boolean background = "fetch".equals(requestedWith);
        String page = sitePath(form.getPagePath());
        String back = "redirect:" + (page != null ? page : "/dashboard");

        if (result.hasErrors()) {
            String problem = result.getAllErrors().getFirst().getDefaultMessage();
            if (background) {
                return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body(problem);
            }
            redirectAttributes.addFlashAttribute("feedbackError", problem);
            return back;
        }

        feedbackService.submit(principal.getId(), form.getKind(), form.getSummary(), form.getDetails(), page);
        if (background) {
            return ResponseEntity.noContent().build();
        }
        redirectAttributes.addFlashAttribute("feedbackSent", true);
        return back;
    }

    static String sitePath(String candidate) {
        if (candidate == null) {
            return null;
        }
        String trimmed = candidate.trim();
        if (trimmed.startsWith("//") || !SITE_PATH.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed;
    }
}
