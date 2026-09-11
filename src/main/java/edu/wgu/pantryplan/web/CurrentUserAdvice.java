package edu.wgu.pantryplan.web;

import edu.wgu.pantryplan.security.AppUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.switchuser.SwitchUserGrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CurrentUserAdvice {

    @ModelAttribute("currentUserName")
    public String currentUserName(@AuthenticationPrincipal AppUserDetails principal) {
        return principal == null ? null : principal.getDisplayName();
    }

    /** The context-relative path lets shared navigation mark its active page. */
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        return requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;
    }

    /**
     * The admin's name while they are viewing the app as someone else, or null.
     * The layout shows a banner with a way back whenever this is set.
     */
    @ModelAttribute("impersonatorName")
    public String impersonatorName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority instanceof SwitchUserGrantedAuthority switched
                    && switched.getSource().getPrincipal() instanceof AppUserDetails admin) {
                return admin.getDisplayName();
            }
        }
        return null;
    }
}
