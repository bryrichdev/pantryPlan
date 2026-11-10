package edu.wgu.pantryprep.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * An upload over the server's own limit fails before any controller runs, so
 * it is caught here and sent back to the page it came from (a recipe's photos,
 * the recipe form, or the import page) with a plain message rather than an
 * error page.
 */
@ControllerAdvice
public class ImportUploadAdvice {

    private static final Pattern PHOTOS = Pattern.compile("^/recipes/(\\d+)/photos/?$");

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(HttpServletRequest request, RedirectAttributes redirect) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Matcher photos = PHOTOS.matcher(path);
        if (photos.matches()) {
            redirect.addFlashAttribute("error", "Those photos are too large to upload together. Each photo can be "
                    + "up to 20 MB; try adding a few at a time.");
            return "redirect:/recipes/" + photos.group(1) + "#photos";
        }
        if (path.equals("/recipes")) {
            redirect.addFlashAttribute("error", "Those photos are too large to upload together. Save the recipe "
                    + "with a few photos, then add the rest on its page.");
            return "redirect:/recipes/new";
        }
        redirect.addFlashAttribute("error", ImportController.tooLargeMessage());
        return "redirect:/import";
    }
}
