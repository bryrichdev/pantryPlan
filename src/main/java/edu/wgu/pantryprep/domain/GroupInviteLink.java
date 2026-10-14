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
 * A shareable join link. Only a SHA-256 digest of its token is kept; the token
 * itself exists in the link that was handed out and nowhere else.
 */
@Entity
@Table(name = "group_invite_links")
public class GroupInviteLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private KitchenGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private GroupRole role;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Null for no limit. */
    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "use_count", nullable = false)
    private int useCount;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected GroupInviteLink() {
    }

    public GroupInviteLink(KitchenGroup group, User createdBy, String tokenHash,
                           GroupRole role, Instant expiresAt, Integer maxUses) {
        this.group = group;
        this.createdBy = createdBy;
        this.tokenHash = tokenHash;
        this.role = role;
        this.expiresAt = expiresAt;
        this.maxUses = maxUses;
    }

    public boolean isUsable(Instant now) {
        return revokedAt == null
                && now.isBefore(expiresAt)
                && (maxUses == null || useCount < maxUses);
    }

    public void revoke(Instant when) {
        if (revokedAt == null) {
            revokedAt = when;
        }
    }

    public KitchenGroup getGroup() {
        return group;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public GroupRole getRole() {
        return role;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public int getUseCount() {
        return useCount;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
