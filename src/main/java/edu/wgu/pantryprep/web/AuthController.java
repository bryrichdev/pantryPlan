package edu.wgu.pantryprep.web;

import edu.wgu.pantryprep.service.EmailAlreadyUsedException;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.RegistrationForm;
import jakarta.validation.Valid;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Registration and login screens.
 */
@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String showLogin() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String showRegister(Model model) {
        model.addAttribute("form", new RegistrationForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form,
                           BindingResult result,
                           RedirectAttributes redirectAttributes,
                           HttpServletRequest request) {
        if (!form.passwordsMatch()) {
            result.rejectValue("confirmPassword", "passwords.mismatch",
                    "Passwords do not match");
        }
        if (result.hasErrors()) {
            return "auth/register";
        }
        try {
            userService.register(form);
            request.login(form.getEmail(), form.getPassword());
        } catch (EmailAlreadyUsedException ex) {
            result.rejectValue("email", "email.taken",
                    "That email already has an account. Sign in instead.");
            return "auth/register";
        } catch (ServletException e) {
            result.reject("auth.failed", "Account created, but automatic login failed. Please sign in.");
            return "auth/login";
        }
        redirectAttributes.addFlashAttribute("registered", true);
        return "redirect:/dashboard";
    }
}
