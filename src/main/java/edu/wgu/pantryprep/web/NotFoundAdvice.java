package edu.wgu.pantryprep.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Turns "no such record for this account" into a 404.
 *
 * <p>Every service lookup scoped to an owner throws NoSuchElementException,
 * whether the id does not exist or belongs to someone else. Both are answered
 * the same way, so a response never reveals that another account's record
 * exists.
 *
 * <p>The handler sends the status rather than rendering a view itself. The
 * container then renders templates/error/404.html through Spring Boot's error
 * controller, the same page an unknown URL gets, with the usual navigation.
 */
@ControllerAdvice
public class NotFoundAdvice {

    @ExceptionHandler(NoSuchElementException.class)
    public void notFound(HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}
