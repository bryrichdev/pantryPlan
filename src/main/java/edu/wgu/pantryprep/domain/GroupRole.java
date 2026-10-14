package edu.wgu.pantryprep.domain;

import java.util.List;

/**
 * A member's standing in a group, highest first.
 *
 * <p>Every permission question about groups is answered here, so the rules
 * live in one place and the services only ask. The database stores the name
 * and limits it to these four values.
 *
 * <table>
 *   <caption>What each role may do</caption>
 *   <tr><th></th><th>Owner</th><th>Admin</th><th>Member</th><th>Viewer</th></tr>
 *   <tr><td>View shared recipes and copy them</td><td>yes</td><td>yes</td><td>yes</td><td>yes</td></tr>
 *   <tr><td>Share their own recipes</td><td>yes</td><td>yes</td><td>yes</td><td>no</td></tr>
 *   <tr><td>Invite, remove lower roles, unshare any recipe</td><td>yes</td><td>yes</td><td>no</td><td>no</td></tr>
 *   <tr><td>Rename, transfer, or delete the group</td><td>yes</td><td>no</td><td>no</td><td>no</td></tr>
 * </table>
 */
public enum GroupRole {

    OWNER("Owner", 4),
    ADMIN("Admin", 3),
    MEMBER("Member", 2),
    VIEWER("Viewer", 1);

    /** The roles an invite link or email invitation may grant. */
    public static final List<GroupRole> INVITABLE = List.of(MEMBER, VIEWER);

    private final String label;
    private final int rank;

    GroupRole(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    public String getLabel() {
        return label;
    }

    public boolean canShareRecipes() {
        return this != VIEWER;
    }

    /** Invite people, revoke invites, and remove recipes others shared. */
    public boolean canManageMembers() {
        return this == OWNER || this == ADMIN;
    }

    /** Rename, transfer, or delete the group. */
    public boolean canEditGroup() {
        return this == OWNER;
    }

    /** Only the owner cannot simply leave: the group would have no owner. */
    public boolean canLeave() {
        return this != OWNER;
    }

    /**
     * Whether someone in this role may remove, or change the role of, someone
     * in the other. Only strictly lower roles, so admins cannot act on each
     * other and nobody can act on the owner.
     */
    public boolean outranksForManagement(GroupRole other) {
        return canManageMembers() && rank > other.rank;
    }

    /** The roles this role may hand to someone it outranks. */
    public List<GroupRole> assignableRoles() {
        return switch (this) {
            case OWNER -> List.of(ADMIN, MEMBER, VIEWER);
            case ADMIN -> List.of(MEMBER, VIEWER);
            default -> List.of();
        };
    }

    public boolean canAssign(GroupRole role) {
        return assignableRoles().contains(role);
    }

    public int getRank() {
        return rank;
    }
}
