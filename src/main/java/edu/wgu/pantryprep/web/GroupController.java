package edu.wgu.pantryprep.web;

import edu.wgu.pantryprep.domain.GroupInvitation;
import edu.wgu.pantryprep.domain.GroupMember;
import edu.wgu.pantryprep.domain.GroupRole;
import edu.wgu.pantryprep.domain.KitchenGroup;
import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.GroupInviteService;
import edu.wgu.pantryprep.service.GroupRuleException;
import edu.wgu.pantryprep.service.GroupService;
import edu.wgu.pantryprep.service.SharedRecipeService;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.EmailInviteForm;
import edu.wgu.pantryprep.web.form.GroupForm;
import edu.wgu.pantryprep.web.form.InviteLinkForm;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Groups: the list of your groups, one group's page, joining, and the
 * read-only view of a recipe someone shared.
 *
 * <p>The controller decides nothing about permissions. It shows or hides
 * controls by role for convenience, but every action goes to a service that
 * re-reads the actor's membership and checks the role itself.
 */
@Controller
@RequestMapping("/groups")
public class GroupController {

    private final GroupService groupService;
    private final GroupInviteService inviteService;
    private final SharedRecipeService sharedRecipeService;
    private final UserService userService;

    public GroupController(GroupService groupService,
                           GroupInviteService inviteService,
                           SharedRecipeService sharedRecipeService,
                           UserService userService) {
        this.groupService = groupService;
        this.inviteService = inviteService;
        this.sharedRecipeService = sharedRecipeService;
        this.userService = userService;
    }

    private User currentUser(AppUserDetails principal) {
        return userService.requireById(principal.getId());
    }

    // ------------------------------------------------------------ your groups

    @GetMapping
    public String list(@AuthenticationPrincipal AppUserDetails principal, Model model) {
        User user = currentUser(principal);
        if (!model.containsAttribute("groupForm")) {
            model.addAttribute("groupForm", new GroupForm());
        }
        return renderList(user, model);
    }

    private String renderList(User user, Model model) {
        List<GroupMember> memberships = groupService.membershipsOf(user);
        Map<Long, Long> memberCounts = new HashMap<>();
        for (GroupMember membership : memberships) {
            memberCounts.put(membership.getGroup().getId(), groupService.memberCount(membership.getGroup()));
        }
        model.addAttribute("memberships", memberships);
        model.addAttribute("memberCounts", memberCounts);
        model.addAttribute("invitations", inviteService.invitationsFor(user));
        model.addAttribute("ownsGroup", groupService.ownsAGroup(user));
        return "groups/list";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AppUserDetails principal,
                         @Valid @ModelAttribute("groupForm") GroupForm form,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        if (result.hasErrors()) {
            return renderList(user, model);
        }
        try {
            KitchenGroup group = groupService.create(form.getName(), form.getDescription(), user);
            redirectAttributes.addFlashAttribute("message", "Group created. Invite people from here.");
            return "redirect:/groups/" + group.getId();
        } catch (GroupRuleException ex) {
            model.addAttribute("error", ex.getMessage());
            return renderList(user, model);
        }
    }

    @PostMapping("/invitations/{invitationId}/accept")
    public String accept(@AuthenticationPrincipal AppUserDetails principal,
                         @PathVariable Long invitationId,
                         RedirectAttributes redirectAttributes) {
        try {
            KitchenGroup group = inviteService.accept(invitationId, currentUser(principal));
            redirectAttributes.addFlashAttribute("message", "You joined " + group.getName() + ".");
            return "redirect:/groups/" + group.getId();
        } catch (GroupRuleException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/groups";
        }
    }

    @PostMapping("/invitations/{invitationId}/decline")
    public String decline(@AuthenticationPrincipal AppUserDetails principal,
                          @PathVariable Long invitationId,
                          RedirectAttributes redirectAttributes) {
        inviteService.decline(invitationId, currentUser(principal));
        redirectAttributes.addFlashAttribute("message", "Invitation declined.");
        return "redirect:/groups";
    }

    // ---------------------------------------------------------------- joining

    /**
     * Shows where a link leads. Joining is a separate POST, so a link preview
     * fetched by a chat app or a browser prefetch never joins anyone.
     */
    @GetMapping("/join/{token}")
    public String joinPage(@AuthenticationPrincipal AppUserDetails principal,
                           @PathVariable String token,
                           Model model) {
        model.addAttribute("token", token);
        model.addAttribute("preview", inviteService.preview(token, currentUser(principal)).orElse(null));
        return "groups/join";
    }

    @PostMapping("/join/{token}")
    public String join(@AuthenticationPrincipal AppUserDetails principal,
                       @PathVariable String token,
                       RedirectAttributes redirectAttributes) {
        try {
            KitchenGroup group = inviteService.joinByLink(token, currentUser(principal));
            redirectAttributes.addFlashAttribute("message", "Welcome to " + group.getName() + ".");
            return "redirect:/groups/" + group.getId();
        } catch (GroupRuleException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/groups";
        }
    }

    // -------------------------------------------------------------- one group

    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal AppUserDetails principal,
                         @PathVariable Long id,
                         Model model) {
        User user = currentUser(principal);
        GroupMember me = groupService.requireMembership(id, user);
        KitchenGroup group = me.getGroup();
        List<GroupMember> members = groupService.members(group);
        List<Recipe> recipes = sharedRecipeService.recipesIn(group);

        Set<Long> unshareable = new HashSet<>();
        for (Recipe recipe : recipes) {
            if (sharedRecipeService.canUnshare(me, recipe, user)) {
                unshareable.add(recipe.getId());
            }
        }

        model.addAttribute("group", group);
        model.addAttribute("me", me);
        model.addAttribute("myUserId", user.getId());
        model.addAttribute("members", members);
        model.addAttribute("recipes", recipes);
        model.addAttribute("unshareable", unshareable);

        if (me.getRole().canManageMembers()) {
            model.addAttribute("links", inviteService.activeLinks(group));
            model.addAttribute("invitations", inviteService.openInvitations(group));
            model.addAttribute("invitableRoles", GroupRole.INVITABLE);
            model.addAttribute("lifetimes", GroupInviteService.LINK_LIFETIME_DAYS);
            if (!model.containsAttribute("linkForm")) {
                model.addAttribute("linkForm", new InviteLinkForm());
            }
            if (!model.containsAttribute("emailForm")) {
                model.addAttribute("emailForm", new EmailInviteForm());
            }
        }
        if (me.getRole().canEditGroup()) {
            if (!model.containsAttribute("groupForm")) {
                model.addAttribute("groupForm", GroupForm.from(group));
            }
            model.addAttribute("transferCandidates", members.stream()
                    .filter(m -> !m.getId().equals(me.getId()))
                    .toList());
        }
        return "groups/detail";
    }

    @PostMapping("/{id}/edit")
    public String edit(@AuthenticationPrincipal AppUserDetails principal,
                       @PathVariable Long id,
                       @Valid @ModelAttribute("groupForm") GroupForm form,
                       BindingResult result,
                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", firstError(result));
            return "redirect:/groups/" + id;
        }
        groupService.update(id, form.getName(), form.getDescription(), currentUser(principal));
        redirectAttributes.addFlashAttribute("message", "Group details saved.");
        return "redirect:/groups/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails principal,
                         @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        groupService.delete(id, currentUser(principal));
        redirectAttributes.addFlashAttribute("message",
                "Group deleted. Everyone's recipes are still in their own accounts.");
        return "redirect:/groups";
    }

    @PostMapping("/{id}/transfer")
    public String transfer(@AuthenticationPrincipal AppUserDetails principal,
                           @PathVariable Long id,
                           @RequestParam("memberId") Long memberId,
                           RedirectAttributes redirectAttributes) {
        return attempt(redirectAttributes, "redirect:/groups/" + id, () -> {
            groupService.transferOwnership(id, memberId, currentUser(principal));
            return "Ownership transferred. You are now an admin of this group.";
        });
    }

    @PostMapping("/{id}/leave")
    public String leave(@AuthenticationPrincipal AppUserDetails principal,
                        @PathVariable Long id,
                        RedirectAttributes redirectAttributes) {
        try {
            groupService.leave(id, currentUser(principal));
            redirectAttributes.addFlashAttribute("message",
                    "You left the group. Recipes you shared there are no longer shared.");
            return "redirect:/groups";
        } catch (GroupRuleException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/groups/" + id;
        }
    }

    // ---------------------------------------------------------------- members

    @PostMapping("/{id}/members/{memberId}/role")
    public String changeRole(@AuthenticationPrincipal AppUserDetails principal,
                             @PathVariable Long id,
                             @PathVariable Long memberId,
                             @RequestParam("role") GroupRole role,
                             RedirectAttributes redirectAttributes) {
        GroupMember changed = groupService.changeRole(id, memberId, role, currentUser(principal));
        String note = role == GroupRole.VIEWER
                ? " Recipes they had shared here were taken out."
                : "";
        redirectAttributes.addFlashAttribute("message",
                changed.getUser().getDisplayName() + " is now " + article(role) + " " + role.getLabel().toLowerCase() + "." + note);
        return "redirect:/groups/" + id;
    }

    @PostMapping("/{id}/members/{memberId}/remove")
    public String removeMember(@AuthenticationPrincipal AppUserDetails principal,
                               @PathVariable Long id,
                               @PathVariable Long memberId,
                               RedirectAttributes redirectAttributes) {
        groupService.removeMember(id, memberId, currentUser(principal));
        redirectAttributes.addFlashAttribute("message", "Removed from the group.");
        return "redirect:/groups/" + id;
    }

    // ---------------------------------------------------------------- invites

    @PostMapping("/{id}/links")
    public String createLink(@AuthenticationPrincipal AppUserDetails principal,
                             @PathVariable Long id,
                             @Valid @ModelAttribute("linkForm") InviteLinkForm form,
                             BindingResult result,
                             RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", firstError(result));
            return "redirect:/groups/" + id;
        }
        if (!GroupInviteService.LINK_LIFETIME_DAYS.contains(form.getLifetimeDays())) {
            redirectAttributes.addFlashAttribute("error", "Choose how long the link works from the list.");
            return "redirect:/groups/" + id;
        }
        try {
            GroupInviteService.CreatedLink created = inviteService.createLink(
                    id, currentUser(principal), form.getRole(), form.getLifetimeDays(), form.getMaxUses());
            String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/groups/join/{token}")
                    .buildAndExpand(created.token())
                    .toUriString();
            redirectAttributes.addFlashAttribute("newLinkUrl", url);
        } catch (GroupRuleException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/groups/" + id + "#invite";
    }

    @PostMapping("/{id}/links/{linkId}/revoke")
    public String revokeLink(@AuthenticationPrincipal AppUserDetails principal,
                             @PathVariable Long id,
                             @PathVariable Long linkId,
                             RedirectAttributes redirectAttributes) {
        inviteService.revokeLink(id, linkId, currentUser(principal));
        redirectAttributes.addFlashAttribute("message", "Link revoked. It no longer works.");
        return "redirect:/groups/" + id + "#invite";
    }

    /**
     * The reply is the same whether or not the email has an account, so this
     * form cannot be used to find out who uses PantryPrep.
     */
    @PostMapping("/{id}/invitations")
    public String invite(@AuthenticationPrincipal AppUserDetails principal,
                         @PathVariable Long id,
                         @Valid @ModelAttribute("emailForm") EmailInviteForm form,
                         BindingResult result,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", firstError(result));
            return "redirect:/groups/" + id + "#invite";
        }
        return attempt(redirectAttributes, "redirect:/groups/" + id + "#invite", () -> {
            GroupInvitation invitation = inviteService.inviteByEmail(
                    id, currentUser(principal), form.getEmail(), form.getRole());
            return "Invitation saved for " + invitation.getEmail()
                    + ". It will be on their Groups page when they sign in with that email.";
        });
    }

    @PostMapping("/{id}/invitations/{invitationId}/revoke")
    public String revokeInvitation(@AuthenticationPrincipal AppUserDetails principal,
                                   @PathVariable Long id,
                                   @PathVariable Long invitationId,
                                   RedirectAttributes redirectAttributes) {
        inviteService.revokeInvitation(id, invitationId, currentUser(principal));
        redirectAttributes.addFlashAttribute("message", "Invitation withdrawn.");
        return "redirect:/groups/" + id + "#invite";
    }

    // --------------------------------------------------------- shared recipes

    @GetMapping("/{id}/recipes/{recipeId}")
    public String sharedRecipe(@AuthenticationPrincipal AppUserDetails principal,
                               @PathVariable Long id,
                               @PathVariable Long recipeId,
                               Model model) {
        User user = currentUser(principal);
        GroupMember me = groupService.requireMembership(id, user);
        Recipe recipe = sharedRecipeService.requireShared(id, recipeId, user);
        model.addAttribute("group", me.getGroup());
        model.addAttribute("recipe", recipe);
        model.addAttribute("plan", sharedRecipeService.planCopy(recipe, user));
        model.addAttribute("canUnshare", sharedRecipeService.canUnshare(me, recipe, user));
        return "groups/shared-recipe";
    }

    @PostMapping("/{id}/recipes/{recipeId}/copy")
    public String copy(@AuthenticationPrincipal AppUserDetails principal,
                       @PathVariable Long id,
                       @PathVariable Long recipeId,
                       @RequestParam(name = "name", required = false) String name,
                       RedirectAttributes redirectAttributes) {
        try {
            Recipe copy = sharedRecipeService.copyToAccount(id, recipeId, name, currentUser(principal));
            redirectAttributes.addFlashAttribute("message", "Copied to your recipes. It's yours to edit.");
            return "redirect:/recipes/" + copy.getId();
        } catch (GroupRuleException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/groups/" + id + "/recipes/" + recipeId;
        }
    }

    @PostMapping("/{id}/recipes/{recipeId}/unshare")
    public String unshare(@AuthenticationPrincipal AppUserDetails principal,
                          @PathVariable Long id,
                          @PathVariable Long recipeId,
                          RedirectAttributes redirectAttributes) {
        sharedRecipeService.unshare(id, recipeId, currentUser(principal));
        redirectAttributes.addFlashAttribute("message", "Recipe taken out of the group.");
        return "redirect:/groups/" + id;
    }

    // ---------------------------------------------------------------- helpers

    private String attempt(RedirectAttributes redirectAttributes, String target, Supplier<String> action) {
        try {
            redirectAttributes.addFlashAttribute("message", action.get());
        } catch (GroupRuleException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return target;
    }

    private static String firstError(BindingResult result) {
        return result.getAllErrors().isEmpty()
                ? "Check the form and try again."
                : result.getAllErrors().get(0).getDefaultMessage();
    }

    private static String article(GroupRole role) {
        return role == GroupRole.ADMIN ? "an" : "a";
    }
}
