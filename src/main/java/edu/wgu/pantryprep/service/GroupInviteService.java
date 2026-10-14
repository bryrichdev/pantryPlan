package edu.wgu.pantryprep.service;

import edu.wgu.pantryprep.domain.GroupInvitation;
import edu.wgu.pantryprep.domain.GroupInviteLink;
import edu.wgu.pantryprep.domain.GroupMember;
import edu.wgu.pantryprep.domain.GroupRole;
import edu.wgu.pantryprep.domain.KitchenGroup;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.repository.GroupInvitationRepository;
import edu.wgu.pantryprep.repository.GroupInviteLinkRepository;
import edu.wgu.pantryprep.repository.KitchenGroupRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two ways into a group: a shareable link, and an invitation addressed to
 * an email.
 *
 * <p><b>Links.</b> A link carries 256 random bits. Only their SHA-256 digest
 * is stored, so the database, its backups, and the audit log hold nothing
 * that opens a group. Links expire, can be revoked, and can be limited to a
 * number of uses. Joining takes a use with one conditional UPDATE, so the
 * last use cannot be taken twice.
 *
 * <p><b>Email invitations.</b> These name an email, never an account. The
 * person inviting gets the same answer whether or not anyone has signed up
 * with it, so the form cannot be used to find out who uses PantryPrep. The
 * invitation shows on the Groups page of whoever signs in with that email.
 *
 * <p>Neither can make anyone more than a member. Admin is granted by hand.
 */
@Service
public class GroupInviteService {

    public static final List<Integer> LINK_LIFETIME_DAYS = List.of(1, 7, 30);
    public static final int MAX_LINK_USES = 100;
    public static final int MAX_ACTIVE_LINKS = 10;
    public static final int MAX_OPEN_INVITATIONS = 25;
    public static final Duration INVITATION_LIFETIME = Duration.ofDays(14);

    /** 32 random bytes in URL-safe base64 without padding. */
    private static final Pattern TOKEN_SHAPE = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final SecureRandom random = new SecureRandom();
    private final GroupService groupService;
    private final KitchenGroupRepository groupRepository;
    private final GroupInviteLinkRepository linkRepository;
    private final GroupInvitationRepository invitationRepository;

    public GroupInviteService(GroupService groupService,
                              KitchenGroupRepository groupRepository,
                              GroupInviteLinkRepository linkRepository,
                              GroupInvitationRepository invitationRepository) {
        this.groupService = groupService;
        this.groupRepository = groupRepository;
        this.linkRepository = linkRepository;
        this.invitationRepository = invitationRepository;
    }

    /** A new link and its token. The token is not stored and cannot be shown again. */
    public record CreatedLink(GroupInviteLink link, String token) {
    }

    /** What someone holding a link sees before deciding to join. */
    public record LinkPreview(KitchenGroup group, GroupRole role, long memberCount, boolean alreadyMember) {
    }

    // ------------------------------------------------------------------ links

    @Transactional
    public CreatedLink createLink(Long groupId, User actor, GroupRole role, int lifetimeDays, Integer maxUses) {
        GroupMember mine = groupService.requireRole(groupId, actor, GroupRole::canManageMembers, "create invite links");
        if (!GroupRole.INVITABLE.contains(role)) {
            throw new GroupPermissionException("Invite links cannot grant " + role);
        }
        if (!LINK_LIFETIME_DAYS.contains(lifetimeDays)) {
            throw new IllegalArgumentException("Unsupported link lifetime: " + lifetimeDays);
        }
        if (maxUses != null && (maxUses < 1 || maxUses > MAX_LINK_USES)) {
            throw new IllegalArgumentException("Unsupported use limit: " + maxUses);
        }
        Instant now = Instant.now();
        KitchenGroup group = mine.getGroup();
        if (linkRepository.findActive(group, now).size() >= MAX_ACTIVE_LINKS) {
            throw new GroupRuleException("This group already has " + MAX_ACTIVE_LINKS
                    + " working invite links. Revoke one first.");
        }

        String token = newToken();
        GroupInviteLink link = new GroupInviteLink(group, actor, hash(token), role,
                now.plus(Duration.ofDays(lifetimeDays)), maxUses);
        return new CreatedLink(linkRepository.save(link), token);
    }

    @Transactional
    public void revokeLink(Long groupId, Long linkId, User actor) {
        GroupMember mine = groupService.requireRole(groupId, actor, GroupRole::canManageMembers, "revoke invite links");
        GroupInviteLink link = linkRepository.findByIdAndGroup(linkId, mine.getGroup())
                .orElseThrow(() -> new NoSuchElementException("No link " + linkId + " in this group"));
        link.revoke(Instant.now());
        linkRepository.save(link);
    }

    @Transactional(readOnly = true)
    public List<GroupInviteLink> activeLinks(KitchenGroup group) {
        return linkRepository.findActive(group, Instant.now());
    }

    /**
     * @return the group behind a working link, or empty for a link that is
     *         unknown, expired, revoked, or used up; these are not told apart
     */
    @Transactional(readOnly = true)
    public Optional<LinkPreview> preview(String token, User user) {
        return usableLink(token).map(link -> new LinkPreview(
                link.getGroup(),
                link.getRole(),
                groupService.memberCount(link.getGroup()),
                groupService.isMember(link.getGroup(), user)));
    }

    /**
     * Joins through a link. Someone already in the group is let through
     * without using the link up.
     *
     * @throws NoSuchElementException when the link does not work
     * @throws GroupRuleException     when the group or the account is full
     */
    @Transactional
    public KitchenGroup joinByLink(String token, User user) {
        GroupInviteLink link = usableLink(token)
                .orElseThrow(() -> new NoSuchElementException("That invite link does not work"));
        Long groupId = link.getGroup().getId();
        GroupRole role = link.getRole();
        if (groupService.isMember(link.getGroup(), user)) {
            return link.getGroup();
        }
        if (linkRepository.claimUse(link.getId(), Instant.now()) != 1) {
            throw new NoSuchElementException("That invite link was used up a moment ago");
        }
        /* claimUse clears the persistence context, so the group is read again. */
        KitchenGroup group = groupRepository.findById(groupId).orElseThrow();
        groupService.addMember(group, user, role);
        return group;
    }

    private Optional<GroupInviteLink> usableLink(String token) {
        if (token == null || !TOKEN_SHAPE.matcher(token).matches()) {
            return Optional.empty();
        }
        return linkRepository.findByTokenHash(hash(token))
                .filter(link -> link.isUsable(Instant.now()));
    }

    // ------------------------------------------------------ email invitations

    /**
     * Invites an email address. Inviting the same address again renews the
     * invitation rather than adding a second one.
     *
     * @throws GroupRuleException when someone in the group already uses that
     *         email, or too many invitations are waiting. Group managers can
     *         see members' emails anyway, so saying so reveals nothing new.
     */
    @Transactional
    public GroupInvitation inviteByEmail(Long groupId, User actor, String email, GroupRole role) {
        GroupMember mine = groupService.requireRole(groupId, actor, GroupRole::canManageMembers, "invite people");
        if (!GroupRole.INVITABLE.contains(role)) {
            throw new GroupPermissionException("Invitations cannot grant " + role);
        }
        KitchenGroup group = mine.getGroup();
        String address = normalize(email);
        boolean alreadyIn = groupService.members(group).stream()
                .anyMatch(m -> m.getUser().getEmail().equalsIgnoreCase(address));
        if (alreadyIn) {
            throw new GroupRuleException(address + " is already in this group.");
        }

        Instant now = Instant.now();
        Instant expires = now.plus(INVITATION_LIFETIME);
        Optional<GroupInvitation> existing = invitationRepository.findByGroupAndEmail(group, address);
        if (existing.isPresent()) {
            existing.get().renew(actor, role, expires);
            return invitationRepository.save(existing.get());
        }
        if (invitationRepository.countOpenInGroup(group, now) >= MAX_OPEN_INVITATIONS) {
            throw new GroupRuleException("This group already has " + MAX_OPEN_INVITATIONS
                    + " invitations waiting. Revoke some first.");
        }
        return invitationRepository.save(new GroupInvitation(group, actor, address, role, expires));
    }

    @Transactional
    public void revokeInvitation(Long groupId, Long invitationId, User actor) {
        GroupMember mine = groupService.requireRole(groupId, actor, GroupRole::canManageMembers, "revoke invitations");
        GroupInvitation invitation = invitationRepository.findByIdAndGroup(invitationId, mine.getGroup())
                .orElseThrow(() -> new NoSuchElementException("No invitation " + invitationId + " in this group"));
        invitationRepository.delete(invitation);
    }

    @Transactional(readOnly = true)
    public List<GroupInvitation> openInvitations(KitchenGroup group) {
        return invitationRepository.findOpenInGroup(group, Instant.now());
    }

    /** Invitations waiting for this account, matched on its current email. */
    @Transactional(readOnly = true)
    public List<GroupInvitation> invitationsFor(User user) {
        return invitationRepository.findOpenFor(normalize(user.getEmail()), Instant.now());
    }

    /**
     * @throws NoSuchElementException when the invitation is not addressed to
     *         this account's email
     * @throws GroupRuleException     when it has expired, or a limit is reached
     */
    @Transactional
    public KitchenGroup accept(Long invitationId, User user) {
        GroupInvitation invitation = requireAddressedTo(invitationId, user);
        if (invitation.isExpired(Instant.now())) {
            throw new GroupRuleException("That invitation has expired. Ask for a new one.");
        }
        KitchenGroup group = invitation.getGroup();
        if (!groupService.isMember(group, user)) {
            groupService.addMember(group, user, invitation.getRole());
        }
        invitationRepository.delete(invitation);
        return group;
    }

    @Transactional
    public void decline(Long invitationId, User user) {
        invitationRepository.delete(requireAddressedTo(invitationId, user));
    }

    private GroupInvitation requireAddressedTo(Long invitationId, User user) {
        return invitationRepository.findByIdAndEmail(invitationId, normalize(user.getEmail()))
                .orElseThrow(() -> new NoSuchElementException("No invitation " + invitationId + " for this account"));
    }

    // ---------------------------------------------------------------- helpers

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is always available", ex);
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
