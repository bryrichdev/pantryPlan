package edu.wgu.pantryprep.web.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Backing object for the registration form. Constraints here are enforced
 * server side by {@code @Valid}, independently of any browser validation.
 */
public class RegistrationForm {

    @NotBlank(message = "Enter your name")
    @Size(max = 100, message = "Name must be 100 characters or fewer")
    private String displayName;

    @NotBlank(message = "Enter your email address")
    @Email(message = "Enter a valid email address")
    @Size(max = 255, message = "Email must be 255 characters or fewer")
    private String email;

    @NotBlank(message = "Choose a password")
    @Size(min = 10, max = 72, message = "Password must be between 10 and 72 characters")
    private String password;

    @NotBlank(message = "Re-enter your password")
    private String confirmPassword;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public boolean passwordsMatch() {
        return password != null && password.equals(confirmPassword);
    }
}
