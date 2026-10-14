package edu.wgu.pantryprep.web.form;

import edu.wgu.pantryprep.domain.GroupRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * A new invite link's settings. The role and lifetime come from fixed lists;
 * the service refuses anything else, whatever the request carried.
 */
public class InviteLinkForm {

    @NotNull(message = "Choose a role")
    private GroupRole role = GroupRole.MEMBER;

    @NotNull(message = "Choose how long the link works")
    private Integer lifetimeDays = 7;

    /** Blank for no limit. */
    @Min(value = 1, message = "Allow at least one use")
    @Max(value = 100, message = "Allow 100 uses or fewer")
    private Integer maxUses;

    public GroupRole getRole() {
        return role;
    }

    public void setRole(GroupRole role) {
        this.role = role;
    }

    public Integer getLifetimeDays() {
        return lifetimeDays;
    }

    public void setLifetimeDays(Integer lifetimeDays) {
        this.lifetimeDays = lifetimeDays;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public void setMaxUses(Integer maxUses) {
        this.maxUses = maxUses;
    }
}
