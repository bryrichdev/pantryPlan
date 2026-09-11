package edu.wgu.pantryplan.security;

import edu.wgu.pantryplan.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Signs out any session whose account no longer exists.
 *
 * <p>A login lives in the HTTP session, so deleting an account does not end
 * sessions already open for it, in another browser or another tab. Without
 * this, the next page that session loads would look the account up, fail, and
 * show "not found". Instead the session is ended and the browser is sent to
 * the sign-in page with an explanation.
 *
 * <p>This costs one primary key lookup per signed-in page request. Static files
 * are skipped.
 */
public class DeletedAccountFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public DeletedAccountFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/css/") || path.startsWith("/js/")
                || path.startsWith("/images/") || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AppUserDetails details
                && !userRepository.existsById(details.getId())) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            response.sendRedirect(request.getContextPath() + "/login?removed");
            return;
        }
        chain.doFilter(request, response);
    }
}
