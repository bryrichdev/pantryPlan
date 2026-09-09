package edu.wgu.pantryplan.web;

import edu.wgu.pantryplan.security.AppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;


@ControllerAdvice
public class CurrentUserAdvice {

    @ModelAttribute("currentUserName")
    public String currentUserName(@AuthenticationPrincipal AppUserDetails principal) {
        return principal == null ? null : principal.getDisplayName();
    }
}