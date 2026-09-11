package edu.wgu.pantryplan.web;

import edu.wgu.pantryplan.security.AppUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
}
