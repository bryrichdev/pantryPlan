package edu.wgu.pantryprep.web;

import edu.wgu.pantryprep.web.ImportController.Kind;
import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * An upload over the server's own limit fails before any controller runs, so
 * it is caught here and sent back to the page it came from (a recipe's photos
 * or an import page) with a plain message rather than an error page.
 */
@ControllerAdvice
public class ImportUploadAdvice {

    private static final Pattern PHOTOS = Pattern.compile("^/recipes/(\\d+)/photos/?$");

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(HttpServletRequest request, RedirectAttributes redirect) {
        Matcher photos = PHOTOS.matcher(request.getRequestURI().substring(request.getContextPath().length()));
        if (photos.matches()) {
            redirect.addFlashAttribute("error", "Those photos are too large to upload together. Each photo can be "
                    + "up to 20 MB; try adding a few at a time.");
            return "redirect:/recipes/" + photos.group(1) + "#photos";
        }
        Kind kind = Kind.of(request);
        redirect.addFlashAttribute("error", ImportController.tooLargeMessage());
        return "redirect:/" + kind.getPath() + "/import";
    }
}
