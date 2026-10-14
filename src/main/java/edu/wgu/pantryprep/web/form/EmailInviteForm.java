package edu.wgu.pantryprep.web.form;

import edu.wgu.pantryprep.domain.GroupRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class EmailInviteForm {

    @NotBlank(message = "Enter an email address")
    @Email(message = "Enter a valid email address")
    @Size(max = 255, message = "Email must be 255 characters or fewer")
    private String email;

    @NotNull(message = "Choose a role")
    private GroupRole role = GroupRole.MEMBER;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public GroupRole getRole() {
        return role;
    }

    public void setRole(GroupRole role) {
        this.role = role;
    }
}
