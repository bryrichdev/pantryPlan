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
 * An invitation addressed to an email. It is matched to whoever is signed in
 * with that email, so the person inviting never learns whether an account
 * exists for it.
 */
@Entity
@Table(name = "group_invitations")
public class GroupInvitation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private KitchenGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_id")
    private User invitedBy;

    /** Always lower case; the database checks this. */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private GroupRole role;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected GroupInvitation() {
    }

    public GroupInvitation(KitchenGroup group, User invitedBy, String email, GroupRole role, Instant expiresAt) {
        this.group = group;
        this.invitedBy = invitedBy;
        this.email = email;
        this.role = role;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** Re-inviting the same email refreshes the existing invitation. */
    public void renew(User invitedBy, GroupRole role, Instant expiresAt) {
        this.invitedBy = invitedBy;
        this.role = role;
        this.expiresAt = expiresAt;
    }

    public KitchenGroup getGroup() {
        return group;
    }

    public User getInvitedBy() {
        return invitedBy;
    }

    public String getEmail() {
        return email;
    }

    public GroupRole getRole() {
        return role;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
