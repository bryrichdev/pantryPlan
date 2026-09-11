package edu.wgu.pantryplan.web.form;

import jakarta.validation.constraints.NotBlank;

/** Confirms deleting your own account by re-entering the password. */
public class AccountDeletionForm {

    @NotBlank(message = "Enter your password to delete the account")
    private String currentPassword;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }
}
