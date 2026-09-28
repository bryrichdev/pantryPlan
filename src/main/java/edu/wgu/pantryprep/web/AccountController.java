package edu.wgu.pantryprep.web;

import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.security.Impersonation;
import edu.wgu.pantryprep.service.AccountDeletionService;
import edu.wgu.pantryprep.service.EmailAlreadyUsedException;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.AccountDeletionForm;
import edu.wgu.pantryprep.web.form.PasswordChangeForm;
import edu.wgu.pantryprep.web.form.ProfileForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The signed-in user's own account: name, email, password, and deletion.
 *
 * <p>Three separate forms on one page, each posting to its own URL, so a
 * mistake in one never discards what was typed into another.
 *
 * <p>Nothing here can be changed while an admin is viewing as this user. The
 * admin sees the page, but every change is refused: an account's sign-in
 * details belong to its owner.
 */
@Controller
@RequestMapping("/account")
public class AccountController {

    private static final String VIEW = "account/settings";
    private static final String IMPERSONATION_REFUSAL =
            "You are viewing as this user, so their sign-in details cannot be changed.";

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AccountController(UserService userService, AccountDeletionService accountDeletionService) {
        this.userService = userService;
        this.accountDeletionService = accountDeletionService;
    }

    @GetMapping
    public String settings(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        User user = userService.requireById(principal.getId());
        ProfileForm profile = new ProfileForm();
        profile.setDisplayName(user.getDisplayName());
        profile.setEmail(user.getEmail());
        return render(model, profile, new PasswordChangeForm(), new AccountDeletionForm());
    }

    @PostMapping("/profile")
    public String updateProfile(@AuthenticationPrincipal AppUserDetails principal,
                                @Valid @ModelAttribute("profileForm") ProfileForm form,
                                BindingResult result,
                                Model model,
                                HttpServletRequest request,
                                HttpServletResponse response,
                                RedirectAttributes redirectAttributes) {
        if (Impersonation.isActive()) {
            redirectAttributes.addFlashAttribute("error", IMPERSONATION_REFUSAL);
            return "redirect:/account";
        }
        User user = userService.requireById(principal.getId());

        boolean emailChanged = form.getEmail() != null
                && !form.getEmail().trim().equalsIgnoreCase(user.getEmail());
        if (emailChanged && !userService.passwordMatches(user, form.getCurrentPassword())) {
            result.rejectValue("currentPassword", "password.required",
                    "Enter your current password to change your email");
        }
        if (emailChanged && !result.hasFieldErrors("email")
                && userService.emailIsTakenByAnother(form.getEmail(), user.getId())) {
            result.rejectValue("email", "email.taken", "Another account already uses that email");
        }
        if (result.hasErrors()) {
            form.setCurrentPassword(null);
            return render(model, form, new PasswordChangeForm(), new AccountDeletionForm());
        }

        try {
            User saved = userService.updateProfile(user.getId(), form.getDisplayName(), form.getEmail());
            refreshLogin(saved, request, response);
        } catch (EmailAlreadyUsedException ex) {
            result.rejectValue("email", "email.taken", "Another account already uses that email");
            form.setCurrentPassword(null);
            return render(model, form, new PasswordChangeForm(), new AccountDeletionForm());
        }
        redirectAttributes.addFlashAttribute("message", "Your details were updated.");
        return "redirect:/account";
    }

    @PostMapping("/password")
    public String changePassword(@AuthenticationPrincipal AppUserDetails principal,
                                 @Valid @ModelAttribute("passwordForm") PasswordChangeForm form,
                                 BindingResult result,
                                 Model model,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 RedirectAttributes redirectAttributes) {
        if (Impersonation.isActive()) {
            redirectAttributes.addFlashAttribute("error", IMPERSONATION_REFUSAL);
            return "redirect:/account";
        }
        User user = userService.requireById(principal.getId());

        if (!result.hasFieldErrors("currentPassword")
                && !userService.passwordMatches(user, form.getCurrentPassword())) {
            result.rejectValue("currentPassword", "password.wrong", "That is not your current password");
        }
        if (form.getNewPassword() != null && !form.getNewPassword().equals(form.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "password.mismatch", "The new passwords do not match");
        }
        if (result.hasErrors()) {
            /* The errors are kept, but typed passwords never go back to the browser. */
            form.setCurrentPassword(null);
            form.setNewPassword(null);
            form.setConfirmPassword(null);
            return render(model, profileOf(user), form, new AccountDeletionForm());
        }

        User saved = userService.changePassword(user.getId(), form.getNewPassword());
        /* A new session id after a credential change, so anyone holding the
           old session cookie is not carried along with it. */
        request.changeSessionId();
        refreshLogin(saved, request, response);
        redirectAttributes.addFlashAttribute("message", "Your password was changed.");
        return "redirect:/account";
    }

    @PostMapping("/delete")
    public String deleteAccount(@AuthenticationPrincipal AppUserDetails principal,
                                @Valid @ModelAttribute("deleteForm") AccountDeletionForm form,
                                BindingResult result,
                                Model model,
                                HttpServletRequest request,
                                HttpServletResponse response,
                                RedirectAttributes redirectAttributes) {
        if (Impersonation.isActive()) {
            redirectAttributes.addFlashAttribute("error", IMPERSONATION_REFUSAL);
            return "redirect:/account";
        }
        User user = userService.requireById(principal.getId());

        if (!result.hasErrors() && !userService.passwordMatches(user, form.getCurrentPassword())) {
            result.rejectValue("currentPassword", "password.wrong", "That is not your password");
        }
        if (!result.hasErrors() && userService.isLastAdmin(user)) {
            result.rejectValue("currentPassword", "admin.last",
                    "You are the only admin. Make another account an admin before deleting this one.");
        }
        if (result.hasErrors()) {
            form.setCurrentPassword(null);
            return render(model, profileOf(user), new PasswordChangeForm(), form);
        }

        accountDeletionService.delete(user);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/login?deleted";
    }

    private String render(Model model, ProfileForm profile, PasswordChangeForm password,
                          AccountDeletionForm deletion) {
        if (!model.containsAttribute("profileForm")) {
            model.addAttribute("profileForm", profile);
        }
        if (!model.containsAttribute("passwordForm")) {
            model.addAttribute("passwordForm", password);
        }
        if (!model.containsAttribute("deleteForm")) {
            model.addAttribute("deleteForm", deletion);
        }
        model.addAttribute("impersonating", Impersonation.isActive());
        return VIEW;
    }

    private ProfileForm profileOf(User user) {
        ProfileForm profile = new ProfileForm();
        profile.setDisplayName(user.getDisplayName());
        profile.setEmail(user.getEmail());
        return profile;
    }

    /**
     * Replaces the session's login with one built from the saved account.
     *
     * <p>The login copies the name and email at sign-in time. Without this, the
     * header would keep the old name and the session would still carry the old
     * email until the next sign-in.
     */
    private void refreshLogin(User user, HttpServletRequest request, HttpServletResponse response) {
        AppUserDetails details = new AppUserDetails(user);
        Authentication updated = UsernamePasswordAuthenticationToken.authenticated(
                details, null, details.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(updated);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
