package edu.wgu.pantryprep.web.form;

import edu.wgu.pantryprep.domain.KitchenGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Name and description, for creating a group and for the owner's edits. */
public class GroupForm {

    @NotBlank(message = "Enter a group name")
    @Size(max = 80, message = "Name must be 80 characters or fewer")
    private String name;

    @Size(max = 500, message = "Description must be 500 characters or fewer")
    private String description;

    public static GroupForm from(KitchenGroup group) {
        GroupForm form = new GroupForm();
        form.setName(group.getName());
        form.setDescription(group.getDescription());
        return form;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
