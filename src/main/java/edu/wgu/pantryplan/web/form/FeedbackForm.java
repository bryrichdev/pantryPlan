package edu.wgu.pantryplan.web.form;

import edu.wgu.pantryplan.domain.FeedbackKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The report dialog behind the button in the corner of every page. The page
 * path is filled in by the page itself, so admins can see where the sender was.
 */
public class FeedbackForm {

    @NotNull(message = "Choose whether something is broken or you want a change")
    private FeedbackKind kind;

    @NotBlank(message = "Add a short summary")
    @Size(max = 120, message = "Keep the summary to 120 characters or fewer")
    private String summary;

    @Size(max = 4000, message = "Keep the details to 4000 characters or fewer")
    private String details;

    private String pagePath;

    public FeedbackKind getKind() {
        return kind;
    }

    public void setKind(FeedbackKind kind) {
        this.kind = kind;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getPagePath() {
        return pagePath;
    }

    public void setPagePath(String pagePath) {
        this.pagePath = pagePath;
    }
}
