package edu.wgu.pantryprep.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A problem report or change request sent to the admins.
 *
 * <p>The sender's email is copied onto the report when it is sent. The link to
 * the account is cleared if the account is deleted, and the email keeps the
 * report readable after that.
 */
@Entity
@Table(name = "feedback")
public class Feedback extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "sender_email", nullable = false, length = 255)
    private String senderEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private FeedbackKind kind;

    @Column(name = "summary", nullable = false, length = 120)
    private String summary;

    @Column(name = "details", length = 4000)
    private String details;

    @Column(name = "page_path", length = 255)
    private String pagePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FeedbackStatus status = FeedbackStatus.OPEN;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Feedback() {
        // required by JPA
    }

    public Feedback(User user, FeedbackKind kind, String summary, String details, String pagePath) {
        this.user = user;
        this.senderEmail = user.getEmail();
        this.kind = kind;
        this.summary = summary;
        this.details = details;
        this.pagePath = pagePath;
    }

    public void resolve() {
        this.status = FeedbackStatus.RESOLVED;
        this.resolvedAt = Instant.now();
    }

    public void reopen() {
        this.status = FeedbackStatus.OPEN;
        this.resolvedAt = null;
    }

    public boolean isOpen() {
        return status == FeedbackStatus.OPEN;
    }

    public User getUser() {
        return user;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public FeedbackKind getKind() {
        return kind;
    }

    public String getSummary() {
        return summary;
    }

    public String getDetails() {
        return details;
    }

    public String getPagePath() {
        return pagePath;
    }

    public FeedbackStatus getStatus() {
        return status;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
