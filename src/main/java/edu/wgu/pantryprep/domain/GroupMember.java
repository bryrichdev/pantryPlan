package edu.wgu.pantryprep.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One user's place in one group. The owner has a row too, with role OWNER. */
@Entity
@Table(name = "group_members")
public class GroupMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private KitchenGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private GroupRole role;

    protected GroupMember() {
    }

    public GroupMember(KitchenGroup group, User user, GroupRole role) {
        this.group = group;
        this.user = user;
        this.role = role;
    }

    public KitchenGroup getGroup() {
        return group;
    }

    public User getUser() {
        return user;
    }

    public GroupRole getRole() {
        return role;
    }

    public void setRole(GroupRole role) {
        this.role = role;
    }
}
