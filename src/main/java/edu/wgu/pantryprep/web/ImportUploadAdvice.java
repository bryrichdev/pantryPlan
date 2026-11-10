package edu.wgu.pantryprep.web;

import edu.wgu.pantryprep.web.ImportController.Kind;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * An upload over the server's own limit fails before any controller runs, so
 * it is caught here and sent back to the import page it came from with a
 * plain message rather than an error page.
 */
@ControllerAdvice
public class ImportUploadAdvice {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(HttpServletRequest request, RedirectAttributes redirect) {
        Kind kind = Kind.of(request);
        redirect.addFlashAttribute("error", ImportController.tooLargeMessage());
        return "redirect:/" + kind.getPath() + "/import";
    }
}
