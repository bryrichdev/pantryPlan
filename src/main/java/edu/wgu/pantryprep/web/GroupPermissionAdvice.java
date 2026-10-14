package edu.wgu.pantryprep.web;

import edu.wgu.pantryprep.service.GroupPermissionException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Answers a group action the member's role does not allow with 403 and the
 * usual "Not allowed" page. The pages never offer these actions, so each one
 * is logged: it means a stale page or a hand-made request.
 */
@ControllerAdvice
public class GroupPermissionAdvice {

    private static final Logger log = LoggerFactory.getLogger(GroupPermissionAdvice.class);

    @ExceptionHandler(GroupPermissionException.class)
    public void forbidden(GroupPermissionException ex, HttpServletResponse response) throws IOException {
        log.warn("Refused group action: {}", ex.getMessage());
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
}
