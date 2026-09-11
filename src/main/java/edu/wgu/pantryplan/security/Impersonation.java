package edu.wgu.pantryplan.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.switchuser.SwitchUserGrantedAuthority;

/**
 * Answers "is an admin viewing the app as this account right now?".
 *
 * <p>SwitchUserFilter marks a switched session with a special authority that
 * carries the admin's own login. Anything that must behave differently while
 * an admin is looking, such as refusing to change the account's password,
 * asks here.
 */
public final class Impersonation {

    private Impersonation() {
    }

    /** True when the current request belongs to an admin viewing as someone else. */
    public static boolean isActive() {
        return sourceOf(SecurityContextHolder.getContext().getAuthentication()) != null;
    }

    /** The admin's own login inside a switched session, or null. */
    public static Authentication sourceOf(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority instanceof SwitchUserGrantedAuthority switched) {
                return switched.getSource();
            }
        }
        return null;
    }
}
