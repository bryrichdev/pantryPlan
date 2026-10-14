package edu.wgu.pantryprep.service;

import edu.wgu.pantryprep.domain.GroupMember;
import edu.wgu.pantryprep.domain.GroupRole;
import edu.wgu.pantryprep.domain.KitchenGroup;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.repository.GroupMemberRepository;
import edu.wgu.pantryprep.repository.KitchenGroupRepository;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Groups and the people in them.
 *
 * <p>Every action starts from the acting user's own membership row, read
 * fresh in the same transaction, and asks {@link GroupRole} whether that role
 * may do it. Nothing is decided from what the page sent about roles.
 *
 * <p>Someone outside a group gets {@link NoSuchElementException} for anything
 * in it, the same as a group that does not exist. A member whose role falls
 * short gets {@link GroupPermissionException}.
 */
@Service
public class GroupService {

    /** Keeps one group from growing into a broadcast list. */
    public static final int MAX_MEMBERS = 50;

    /** How many groups one account can be in, its own included. */
    public static final int MAX_GROUPS_PER_USER = 20;

    private static final Comparator<GroupMember> BY_ROLE_THEN_NAME =
            Comparator.comparingInt((GroupMember m) -> -m.getRole().getRank())
                    .thenComparing(m -> m.getUser().getDisplayName(), String.CASE_INSENSITIVE_ORDER);

    private final KitchenGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;

    public GroupService(KitchenGroupRepository groupRepository,
                        GroupMemberRepository memberRepository) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public List<GroupMember> membershipsOf(User user) {
        return memberRepository.findAllForUser(user);
    }

    /** Groups this user may share a recipe into: every role except viewer. */
    @Transactional(readOnly = true)
    public List<KitchenGroup> shareableGroups(User user) {
        return memberRepository.findAllForUser(user).stream()
                .filter(m -> m.getRole().canShareRecipes())
                .map(GroupMember::getGroup)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean ownsAGroup(User user) {
        return groupRepository.existsByOwner(user);
    }

    /**
     * @throws NoSuchElementException when the user is not in the group, or
     *         there is no such group; the two are not told apart
     */
    @Transactional(readOnly = true)
    public GroupMember requireMembership(Long groupId, User user) {
        return memberRepository.findMembership(groupId, user)
                .orElseThrow(() -> new NoSuchElementException("No group " + groupId + " for this account"));
    }

    /** Owner first, then admins, members, and viewers, each by name. */
    @Transactional(readOnly = true)
    public List<GroupMember> members(KitchenGroup group) {
        return memberRepository.findAllInGroup(group).stream()
                .sorted(BY_ROLE_THEN_NAME)
                .toList();
    }

    @Transactional(readOnly = true)
    public long memberCount(KitchenGroup group) {
        return memberRepository.countByGroup(group);
    }

    // ------------------------------------------------------- the group itself

    /**
     * @throws GroupRuleException when the user already owns a group or is in
     *         as many groups as allowed
     */
    @Transactional
    public KitchenGroup create(String name, String description, User owner) {
        if (groupRepository.existsByOwner(owner)) {
            throw new GroupRuleException("You already have a group. Each account can create one.");
        }
        requireRoomForAnotherGroup(owner);
        KitchenGroup group = new KitchenGroup(owner, name.trim());
        group.setDescription(emptyToNull(description));
        group = groupRepository.save(group);
        memberRepository.save(new GroupMember(group, owner, GroupRole.OWNER));
        return group;
    }

    @Transactional
    public KitchenGroup update(Long groupId, String name, String description, User actor) {
        GroupMember mine = requireRole(groupId, actor, GroupRole::canEditGroup, "change this group");
        KitchenGroup group = mine.getGroup();
        group.setName(name.trim());
        group.setDescription(emptyToNull(description));
        return groupRepository.save(group);
    }

    /**
     * Removes the group with its memberships, invites, and shares. The
     * recipes themselves stay with the people who own them.
     */
    @Transactional
    public void delete(Long groupId, User actor) {
        GroupMember mine = requireRole(groupId, actor, GroupRole::canEditGroup, "delete this group");
        groupRepository.deleteGroup(mine.getGroup().getId());
    }

    /**
     * Hands the group to another member. The old owner stays on as an admin.
     *
     * <p>Two partial unique indexes allow one OWNER row per group and per
     * user, so the old owner is stepped down and flushed before the new one
     * steps up; doing both in one flush could trip the index in between.
     *
     * @throws GroupRuleException when the new owner already owns a group
     */
    @Transactional
    public void transferOwnership(Long groupId, Long newOwnerMemberId, User actor) {
        GroupMember mine = requireRole(groupId, actor, GroupRole::canEditGroup, "transfer this group");
        KitchenGroup group = mine.getGroup();
        GroupMember next = requireMember(group, newOwnerMemberId);
        if (next.getId().equals(mine.getId())) {
            throw new GroupRuleException("You already own this group.");
        }
        if (groupRepository.existsByOwner(next.getUser())) {
            throw new GroupRuleException(next.getUser().getDisplayName()
                    + " already owns a group, and an account can own only one.");
        }

        mine.setRole(GroupRole.ADMIN);
        memberRepository.saveAndFlush(mine);

        next.setRole(GroupRole.OWNER);
        memberRepository.saveAndFlush(next);

        group.setOwner(next.getUser());
        groupRepository.saveAndFlush(group);
    }

    // ---------------------------------------------------------------- members

    /**
     * @throws GroupRuleException for the owner, who must transfer the group or
     *         delete it instead
     */
    @Transactional
    public void leave(Long groupId, User user) {
        GroupMember mine = requireMembership(groupId, user);
        if (!mine.getRole().canLeave()) {
            throw new GroupRuleException(
                    "Owners cannot leave their own group. Transfer it to someone else or delete it.");
        }
        memberRepository.delete(mine);
    }

    /**
     * Removes someone of a lower role. What they shared into the group is
     * withdrawn by the database as their membership goes.
     */
    @Transactional
    public void removeMember(Long groupId, Long memberId, User actor) {
        GroupMember mine = requireRole(groupId, actor, GroupRole::canManageMembers, "remove members");
        GroupMember target = requireMember(mine.getGroup(), memberId);
        if (target.getId().equals(mine.getId())) {
            throw new GroupPermissionException("Use leave to remove yourself");
        }
        if (!mine.getRole().outranksForManagement(target.getRole())) {
            throw new GroupPermissionException("A " + mine.getRole() + " cannot remove a " + target.getRole());
        }
        memberRepository.delete(target);
    }

    /**
     * Changes the role of someone of a lower role, to a role the actor may
     * hand out. Ownership moves only through {@link #transferOwnership}.
     */
    @Transactional
    public GroupMember changeRole(Long groupId, Long memberId, GroupRole newRole, User actor) {
        GroupMember mine = requireRole(groupId, actor, GroupRole::canManageMembers, "change roles");
        GroupMember target = requireMember(mine.getGroup(), memberId);
        if (target.getId().equals(mine.getId())) {
            throw new GroupPermissionException("Members cannot change their own role");
        }
        if (newRole == null
                || !mine.getRole().outranksForManagement(target.getRole())
                || !mine.getRole().canAssign(newRole)) {
            throw new GroupPermissionException(
                    "A " + mine.getRole() + " cannot make a " + target.getRole() + " a " + newRole);
        }
        target.setRole(newRole);
        GroupMember saved = memberRepository.save(target);
        saved.getUser().getDisplayName();
        return saved;
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Adds someone to a group for the invite service, after the checks every
     * way of joining shares.
     */
    @Transactional
    public GroupMember addMember(KitchenGroup group, User user, GroupRole role) {
        if (!GroupRole.INVITABLE.contains(role)) {
            throw new IllegalArgumentException("Invites cannot grant " + role);
        }
        if (memberRepository.countByGroup(group) >= MAX_MEMBERS) {
            throw new GroupRuleException(group.getName() + " is full. A group can have up to "
                    + MAX_MEMBERS + " people.");
        }
        requireRoomForAnotherGroup(user);
        return memberRepository.save(new GroupMember(group, user, role));
    }

    @Transactional(readOnly = true)
    public boolean isMember(KitchenGroup group, User user) {
        return memberRepository.existsByGroupAndUser(group, user);
    }

    /**
     * @throws GroupPermissionException when the user is in the group but their
     *         role does not allow the action
     */
    @Transactional(readOnly = true)
    public GroupMember requireRole(Long groupId, User user, Predicate<GroupRole> allowed, String action) {
        GroupMember mine = requireMembership(groupId, user);
        if (!allowed.test(mine.getRole())) {
            throw new GroupPermissionException("A " + mine.getRole() + " cannot " + action);
        }
        return mine;
    }

    private GroupMember requireMember(KitchenGroup group, Long memberId) {
        return memberRepository.findByIdAndGroup(memberId, group)
                .orElseThrow(() -> new NoSuchElementException("No member " + memberId + " in this group"));
    }

    private void requireRoomForAnotherGroup(User user) {
        if (memberRepository.countByUser(user) >= MAX_GROUPS_PER_USER) {
            throw new GroupRuleException("An account can be in up to " + MAX_GROUPS_PER_USER
                    + " groups. Leave one before joining another.");
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** For callers that only need to know whether a lookup would succeed. */
    @Transactional(readOnly = true)
    public Optional<GroupMember> findMembership(Long groupId, User user) {
        return memberRepository.findMembership(groupId, user);
    }
}
