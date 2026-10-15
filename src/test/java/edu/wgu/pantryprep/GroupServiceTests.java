package edu.wgu.pantryprep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wgu.pantryprep.domain.GroupMember;
import edu.wgu.pantryprep.domain.GroupRole;
import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.IngredientCategory;
import edu.wgu.pantryprep.domain.KitchenGroup;
import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.RecipeIcon;
import edu.wgu.pantryprep.domain.RecipeLine;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.repository.IngredientRepository;
import edu.wgu.pantryprep.repository.KitchenGroupRepository;
import edu.wgu.pantryprep.repository.RecipeRepository;
import edu.wgu.pantryprep.service.AccountDeletionService;
import edu.wgu.pantryprep.service.GroupInviteService;
import edu.wgu.pantryprep.service.GroupPermissionException;
import edu.wgu.pantryprep.service.GroupRuleException;
import edu.wgu.pantryprep.service.GroupService;
import edu.wgu.pantryprep.service.IngredientService;
import edu.wgu.pantryprep.service.RecipeService;
import edu.wgu.pantryprep.service.SharedRecipeService;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.IngredientForm;
import edu.wgu.pantryprep.web.form.RecipeForm;
import edu.wgu.pantryprep.web.form.RecipeLineForm;
import edu.wgu.pantryprep.web.form.RegistrationForm;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Groups end to end at the service layer: who may do what, how people join,
 * and what sharing and copying a recipe does to each account.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class GroupServiceTests {

    @Autowired private UserService userService;
    @Autowired private GroupService groupService;
    @Autowired private GroupInviteService inviteService;
    @Autowired private SharedRecipeService sharedRecipeService;
    @Autowired private RecipeService recipeService;
    @Autowired private IngredientService ingredientService;
    @Autowired private AccountDeletionService accountDeletionService;
    @Autowired private RecipeRepository recipeRepository;
    @Autowired private IngredientRepository ingredientRepository;
    @Autowired private KitchenGroupRepository groupRepository;
    @Autowired private EntityManager entityManager;

    // ---------------------------------------------------------------- helpers

    private User cook(String email) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName("Cook " + email.substring(0, email.indexOf('@')));
        form.setEmail(email);
        form.setPassword("correcthorsebattery");
        form.setConfirmPassword("correcthorsebattery");
        return userService.register(form);
    }

    private Ingredient ingredient(User user, String name) {
        IngredientForm form = new IngredientForm();
        form.setName(name);
        form.setCategory(IngredientCategory.PANTRY_STAPLE);
        return ingredientService.create(form, user);
    }

    private RecipeForm recipeForm(String name, Ingredient... ingredients) {
        RecipeForm form = new RecipeForm();
        form.setName(name);
        form.setServings(4);
        for (Ingredient ingredient : ingredients) {
            RecipeLineForm line = new RecipeLineForm();
            line.setIngredientId(ingredient.getId());
            line.setQuantity(new BigDecimal("1"));
            line.setUnit(Unit.CUP);
            form.getLines().add(line);
        }
        return form;
    }

    private Recipe sharedRecipe(User owner, KitchenGroup group, String name, Ingredient... ingredients) {
        RecipeForm form = recipeForm(name, ingredients);
        form.setGroupIds(Set.of(group.getId()));
        return recipeService.create(form, owner);
    }

    /** Joins through a fresh link that grants the given role. */
    private void join(KitchenGroup group, User owner, User joiner, GroupRole role) {
        String token = inviteService.createLink(group.getId(), owner, role, 7, null).token();
        inviteService.joinByLink(token, joiner);
    }

    private GroupMember membership(KitchenGroup group, User user) {
        return groupService.requireMembership(group.getId(), user);
    }

    private List<Recipe> sharedIn(KitchenGroup group) {
        entityManager.flush();
        return sharedRecipeService.recipesIn(group);
    }

    // --------------------------------------------------------------- creating

    @Test
    void creatorBecomesOwnerAndCanCreateUpToFiveGroups() {
        User owner = cook("grp-create@example.com");
        KitchenGroup group = groupService.create("Family", "  ", owner);

        assertEquals(GroupRole.OWNER, membership(group, owner).getRole());
        assertEquals(null, group.getDescription(), "a blank description is stored as nothing");

        for (String name : List.of("Friends", "Book club", "Neighbours", "Work")) {
            KitchenGroup another = groupService.create(name, null, owner);
            assertEquals(GroupRole.OWNER, membership(another, owner).getRole());
        }
        assertEquals(GroupService.MAX_OWNED_GROUPS, groupService.ownedGroupCount(owner));
        assertThrows(GroupRuleException.class, () -> groupService.create("A sixth", null, owner),
                "five is the most one account can create");

        groupService.delete(group.getId(), owner);
        groupService.create("Family again", null, owner);
        assertEquals(GroupService.MAX_OWNED_GROUPS, groupService.ownedGroupCount(owner),
                "deleting one makes room for another");
    }

    @Test
    void theDatabaseRefusesASixthOwnedGroup() {
        User owner = cook("grp-create-db@example.com");
        for (int i = 1; i <= GroupService.MAX_OWNED_GROUPS; i++) {
            groupService.create("Group " + i, null, owner);
        }
        assertThrows(DataIntegrityViolationException.class,
                () -> groupRepository.saveAndFlush(new KitchenGroup(owner, "Sneaked in")),
                "the trigger holds the limit even past the service");
    }

    @Test
    void oneUserCanBelongToManyGroups() {
        User me = cook("grp-many@example.com");
        User mom = cook("grp-many-mom@example.com");
        User pal = cook("grp-many-pal@example.com");
        KitchenGroup family = groupService.create("Family", null, mom);
        KitchenGroup friends = groupService.create("Friends", null, pal);
        groupService.create("Mine", null, me);

        join(family, mom, me, GroupRole.MEMBER);
        join(friends, pal, me, GroupRole.VIEWER);

        assertEquals(3, groupService.membershipsOf(me).size());
    }

    // ---------------------------------------------------------------- joining

    @Test
    void inviteLinksStoreOnlyADigestAndJoinAtTheirRole() {
        User owner = cook("grp-link@example.com");
        User guest = cook("grp-link-guest@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);

        GroupInviteService.CreatedLink created = inviteService.createLink(group.getId(), owner, GroupRole.VIEWER, 7, null);

        assertNotEquals(created.token(), created.link().getTokenHash());
        assertTrue(created.link().getTokenHash().matches("[0-9a-f]{64}"));
        assertTrue(inviteService.preview(created.token(), guest).isPresent());

        inviteService.joinByLink(created.token(), guest);
        assertEquals(GroupRole.VIEWER, membership(group, guest).getRole());
    }

    @Test
    void aLinkStopsWorkingWhenUsedUpOrRevoked() {
        User owner = cook("grp-spent@example.com");
        User first = cook("grp-spent-1@example.com");
        User second = cook("grp-spent-2@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);

        String once = inviteService.createLink(group.getId(), owner, GroupRole.MEMBER, 1, 1).token();
        inviteService.joinByLink(once, first);
        assertThrows(NoSuchElementException.class, () -> inviteService.joinByLink(once, second));

        GroupInviteService.CreatedLink revoked = inviteService.createLink(group.getId(), owner, GroupRole.MEMBER, 7, null);
        inviteService.revokeLink(group.getId(), revoked.link().getId(), owner);
        assertTrue(inviteService.preview(revoked.token(), second).isEmpty());
        assertThrows(NoSuchElementException.class, () -> inviteService.joinByLink(revoked.token(), second));

        assertTrue(inviteService.preview("not-a-real-token", second).isEmpty());
    }

    @Test
    void anExpiredLinkDoesNotWork() {
        User owner = cook("grp-expired@example.com");
        User guest = cook("grp-expired-guest@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        GroupInviteService.CreatedLink created = inviteService.createLink(group.getId(), owner, GroupRole.MEMBER, 1, null);

        entityManager.flush();
        entityManager.createNativeQuery("UPDATE group_invite_links SET expires_at = NOW() - INTERVAL '1 minute' WHERE id = ?1")
                .setParameter(1, created.link().getId())
                .executeUpdate();
        entityManager.clear();

        assertThrows(NoSuchElementException.class, () -> inviteService.joinByLink(created.token(), guest));
    }

    @Test
    void linksCannotGrantAdminOrOwner() {
        User owner = cook("grp-nolinkadmin@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);

        assertThrows(GroupPermissionException.class,
                () -> inviteService.createLink(group.getId(), owner, GroupRole.ADMIN, 7, null));
        assertThrows(GroupPermissionException.class,
                () -> inviteService.inviteByEmail(group.getId(), owner, "x@example.com", GroupRole.OWNER));
    }

    @Test
    void emailInvitationsReachOnlyTheAddressedAccount() {
        User owner = cook("grp-email@example.com");
        User invitee = cook("grp-email-invitee@example.com");
        User snoop = cook("grp-email-snoop@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);

        Long invitationId = inviteService
                .inviteByEmail(group.getId(), owner, "  GRP-Email-Invitee@Example.com ", GroupRole.MEMBER)
                .getId();

        assertTrue(inviteService.invitationsFor(snoop).isEmpty());
        assertThrows(NoSuchElementException.class, () -> inviteService.accept(invitationId, snoop));

        assertEquals(1, inviteService.invitationsFor(invitee).size());
        inviteService.accept(invitationId, invitee);
        assertEquals(GroupRole.MEMBER, membership(group, invitee).getRole());
        assertTrue(inviteService.invitationsFor(invitee).isEmpty(), "accepting uses the invitation up");
    }

    @Test
    void anEmailWithNoAccountCannotBeInvited() {
        User owner = cook("grp-noacct@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);

        assertThrows(GroupRuleException.class,
                () -> inviteService.inviteByEmail(group.getId(), owner, "nobody-here@example.com", GroupRole.VIEWER));
        assertTrue(inviteService.openInvitations(group).isEmpty());
    }

    // ------------------------------------------------------------ permissions

    @Test
    void outsidersCannotSeeAGroupAtAll() {
        User owner = cook("grp-outsider@example.com");
        User stranger = cook("grp-outsider-x@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        Recipe chili = sharedRecipe(owner, group, "Chili");

        assertThrows(NoSuchElementException.class, () -> groupService.requireMembership(group.getId(), stranger));
        assertThrows(NoSuchElementException.class,
                () -> sharedRecipeService.requireShared(group.getId(), chili.getId(), stranger));
        assertThrows(NoSuchElementException.class,
                () -> sharedRecipeService.copyToAccount(group.getId(), chili.getId(), null, stranger));
    }

    @Test
    void membersAndViewersCannotManageTheGroup() {
        User owner = cook("grp-perm@example.com");
        User member = cook("grp-perm-m@example.com");
        User viewer = cook("grp-perm-v@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        join(group, owner, member, GroupRole.MEMBER);
        join(group, owner, viewer, GroupRole.VIEWER);
        Long viewerId = membership(group, viewer).getId();

        for (User user : List.of(member, viewer)) {
            assertThrows(GroupPermissionException.class,
                    () -> inviteService.createLink(group.getId(), user, GroupRole.MEMBER, 7, null));
            assertThrows(GroupPermissionException.class,
                    () -> inviteService.inviteByEmail(group.getId(), user, "a@example.com", GroupRole.MEMBER));
            assertThrows(GroupPermissionException.class,
                    () -> groupService.update(group.getId(), "Renamed", null, user));
            assertThrows(GroupPermissionException.class, () -> groupService.delete(group.getId(), user));
        }
        assertThrows(GroupPermissionException.class,
                () -> groupService.removeMember(group.getId(), viewerId, member));
    }

    @Test
    void adminsManageOnlyRolesBelowThem() {
        User owner = cook("grp-admin@example.com");
        User admin = cook("grp-admin-a@example.com");
        User otherAdmin = cook("grp-admin-b@example.com");
        User member = cook("grp-admin-m@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        join(group, owner, admin, GroupRole.MEMBER);
        join(group, owner, otherAdmin, GroupRole.MEMBER);
        join(group, owner, member, GroupRole.MEMBER);
        groupService.changeRole(group.getId(), membership(group, admin).getId(), GroupRole.ADMIN, owner);
        groupService.changeRole(group.getId(), membership(group, otherAdmin).getId(), GroupRole.ADMIN, owner);

        Long ownerId = membership(group, owner).getId();
        Long otherAdminId = membership(group, otherAdmin).getId();
        Long memberId = membership(group, member).getId();
        Long adminId = membership(group, admin).getId();

        assertThrows(GroupPermissionException.class,
                () -> groupService.changeRole(group.getId(), memberId, GroupRole.ADMIN, admin),
                "admins cannot make more admins");
        assertThrows(GroupPermissionException.class,
                () -> groupService.removeMember(group.getId(), otherAdminId, admin));
        assertThrows(GroupPermissionException.class,
                () -> groupService.removeMember(group.getId(), ownerId, admin));
        assertThrows(GroupPermissionException.class,
                () -> groupService.changeRole(group.getId(), adminId, GroupRole.OWNER, owner),
                "ownership only moves by transfer");

        groupService.changeRole(group.getId(), memberId, GroupRole.VIEWER, admin);
        assertEquals(GroupRole.VIEWER, membership(group, member).getRole());
        groupService.removeMember(group.getId(), memberId, admin);
        assertFalse(groupService.findMembership(group.getId(), member).isPresent());

        String token = inviteService.createLink(group.getId(), admin, GroupRole.MEMBER, 7, 5).token();
        assertTrue(inviteService.preview(token, member).isPresent(), "admins can invite");
    }

    @Test
    void theOwnerMustTransferOrDeleteRatherThanLeave() {
        User owner = cook("grp-xfer@example.com");
        User heir = cook("grp-xfer-h@example.com");
        User busy = cook("grp-xfer-b@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        for (int i = 1; i <= GroupService.MAX_OWNED_GROUPS; i++) {
            groupService.create("Busy's group " + i, null, busy);
        }
        join(group, owner, heir, GroupRole.MEMBER);
        join(group, owner, busy, GroupRole.MEMBER);

        assertThrows(GroupRuleException.class, () -> groupService.leave(group.getId(), owner));
        assertThrows(GroupRuleException.class,
                () -> groupService.transferOwnership(group.getId(), membership(group, busy).getId(), owner),
                "someone who owns five groups cannot take a sixth");

        groupService.transferOwnership(group.getId(), membership(group, heir).getId(), owner);

        assertEquals(GroupRole.OWNER, membership(group, heir).getRole());
        assertEquals(GroupRole.ADMIN, membership(group, owner).getRole());
        assertEquals(heir.getId(), groupRepository.findById(group.getId()).orElseThrow().getOwner().getId());
        assertFalse(groupService.ownsAGroup(owner));
        groupService.create("A fresh start", null, owner);
    }

    // ---------------------------------------------------------------- sharing

    @Test
    void viewersAndOutsidersCannotShareIntoAGroup() {
        User owner = cook("grp-share@example.com");
        User viewer = cook("grp-share-v@example.com");
        User stranger = cook("grp-share-x@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        join(group, owner, viewer, GroupRole.VIEWER);

        assertThrows(NoSuchElementException.class, () -> sharedRecipe(viewer, group, "Toast"));
        assertThrows(NoSuchElementException.class, () -> sharedRecipe(stranger, group, "Toast"));
        assertTrue(groupService.shareableGroups(viewer).isEmpty());
    }

    @Test
    void theDatabaseRefusesAShareTheServiceWouldHaveRefused() {
        User owner = cook("grp-dbshare@example.com");
        User stranger = cook("grp-dbshare-x@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        Recipe theirs = recipeService.create(recipeForm("Toast"), stranger);
        entityManager.flush();

        assertThrows(Exception.class, () -> entityManager
                .createNativeQuery("INSERT INTO recipe_shares (recipe_id, group_id) VALUES (?1, ?2)")
                .setParameter(1, theirs.getId())
                .setParameter(2, group.getId())
                .executeUpdate());
    }

    @Test
    void editingARecipeChangesWhichGroupsSeeIt() {
        User owner = cook("grp-edit@example.com");
        User pal = cook("grp-edit-p@example.com");
        KitchenGroup family = groupService.create("Family", null, owner);
        KitchenGroup friends = groupService.create("Friends", null, pal);
        join(friends, pal, owner, GroupRole.MEMBER);

        Recipe chili = sharedRecipe(owner, family, "Chili");
        RecipeForm edit = RecipeForm.from(recipeService.requireOwned(chili.getId(), owner));
        edit.setGroupIds(Set.of(friends.getId()));
        edit.removeBlankLines();
        recipeService.update(chili.getId(), edit, owner);

        assertTrue(sharedIn(family).isEmpty());
        assertEquals(1, sharedIn(friends).size());
    }

    @Test
    void copyingReusesMatchingIngredientsAndAddsTheRest() {
        User owner = cook("grp-copy@example.com");
        User cousin = cook("grp-copy-c@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        join(group, owner, cousin, GroupRole.VIEWER);

        Recipe chili = sharedRecipe(owner, group, "Chili", ingredient(owner, "Butter"), ingredient(owner, "Cumin"));
        Ingredient cousinsButter = ingredient(cousin, "butter");

        SharedRecipeService.CopyPlan plan = sharedRecipeService.planCopy(
                sharedRecipeService.requireShared(group.getId(), chili.getId(), cousin), cousin);
        assertEquals(List.of("Butter"), plan.reusedIngredients());
        assertEquals(List.of("Cumin"), plan.newIngredients());

        Recipe copy = sharedRecipeService.copyToAccount(group.getId(), chili.getId(), null, cousin);
        Recipe reloaded = recipeService.requireOwned(copy.getId(), cousin);

        assertEquals("Chili", reloaded.getName());
        assertTrue(reloaded.getSharedWithGroups().isEmpty(), "a copy is not shared anywhere");
        for (RecipeLine line : reloaded.getLines()) {
            assertEquals(cousin.getId(), line.getIngredient().getUser().getId(),
                    "every line points at the copier's own ingredient");
        }
        assertTrue(reloaded.getLines().stream()
                .anyMatch(line -> line.getIngredient().getId().equals(cousinsButter.getId())));
        assertEquals(2, ingredientRepository.findAllByUserOrderByNameAsc(cousin).size(),
                "butter was reused, cumin was added, nothing was duplicated");
    }

    @Test
    void aCopyKeepsThePicture() {
        User owner = cook("grp-copy-pic@example.com");
        User cousin = cook("grp-copy-pic-c@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        join(group, owner, cousin, GroupRole.VIEWER);

        RecipeForm form = recipeForm("Chili");
        form.setIcon(RecipeIcon.POT);
        form.setGroupIds(Set.of(group.getId()));
        Recipe chili = recipeService.create(form, owner);

        Recipe copy = sharedRecipeService.copyToAccount(group.getId(), chili.getId(), null, cousin);
        assertEquals(RecipeIcon.POT, recipeService.requireOwned(copy.getId(), cousin).getIcon());
    }

    @Test
    void aCopyNeedsANameTheCopierIsNotUsing() {
        User owner = cook("grp-name@example.com");
        User cousin = cook("grp-name-c@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        join(group, owner, cousin, GroupRole.MEMBER);
        Recipe chili = sharedRecipe(owner, group, "Chili");
        recipeService.create(recipeForm("CHILI"), cousin);

        assertThrows(GroupRuleException.class,
                () -> sharedRecipeService.copyToAccount(group.getId(), chili.getId(), null, cousin));
        assertThrows(GroupRuleException.class,
                () -> sharedRecipeService.copyToAccount(group.getId(), chili.getId(), "Chili", owner),
                "the owner cannot copy their own recipe");

        Recipe copy = sharedRecipeService.copyToAccount(group.getId(), chili.getId(), "Aunt's chili", cousin);
        assertEquals("Aunt's chili", copy.getName());
    }

    @Test
    void sharesAreWithdrawnWhenTheSharerLeavesOrBecomesAViewer() {
        User owner = cook("grp-withdraw@example.com");
        User member = cook("grp-withdraw-m@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        join(group, owner, member, GroupRole.MEMBER);
        sharedRecipe(member, group, "Soup");
        sharedRecipe(owner, group, "Stew");
        assertEquals(2, sharedIn(group).size());

        groupService.changeRole(group.getId(), membership(group, member).getId(), GroupRole.VIEWER, owner);
        assertEquals(1, sharedIn(group).size());

        groupService.changeRole(group.getId(), membership(group, member).getId(), GroupRole.MEMBER, owner);
        Recipe pie = sharedRecipe(member, group, "Pie");
        assertEquals(2, sharedIn(group).size());

        groupService.leave(group.getId(), member);
        assertEquals(1, sharedIn(group).size());
        assertTrue(recipeRepository.findById(pie.getId()).isPresent(), "leaving keeps your recipes");
    }

    @Test
    void aMemberCannotTakeSomeoneElsesRecipeOut() {
        User owner = cook("grp-unshare-x@example.com");
        User a = cook("grp-unshare-xa@example.com");
        User b = cook("grp-unshare-xb@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        join(group, owner, a, GroupRole.MEMBER);
        join(group, owner, b, GroupRole.MEMBER);
        Recipe soup = sharedRecipe(a, group, "Soup");

        assertThrows(GroupPermissionException.class, () -> sharedRecipeService.unshare(group.getId(), soup.getId(), b));
    }

    @Test
    void theSharerOrAManagerCanTakeARecipeOut() {
        User owner = cook("grp-unshare@example.com");
        User a = cook("grp-unshare-a@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        join(group, owner, a, GroupRole.MEMBER);
        Recipe soup = sharedRecipe(a, group, "Soup");
        Recipe stew = sharedRecipe(a, group, "Stew");

        sharedRecipeService.unshare(group.getId(), soup.getId(), a);
        assertEquals(List.of("Stew"), sharedIn(group).stream().map(Recipe::getName).toList(),
                "the sharer took their own recipe out");

        sharedRecipeService.unshare(group.getId(), stew.getId(), owner);
        assertTrue(sharedIn(group).isEmpty(), "the owner took someone else's recipe out");
        assertTrue(recipeRepository.findById(soup.getId()).isPresent(), "unsharing never deletes the recipe");
    }

    @Test
    void bulkCopyRenamesClashesSkipsOwnRecipesAndSharesIngredientsAcrossTheBatch() {
        User owner = cook("grp-bulkcopy@example.com");
        User cousin = cook("grp-bulkcopy-c@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        join(group, owner, cousin, GroupRole.MEMBER);

        Ingredient cumin = ingredient(owner, "Cumin");
        Recipe chili = sharedRecipe(owner, group, "Chili", cumin);
        Recipe tacos = sharedRecipe(owner, group, "Tacos", cumin);
        Recipe mine = sharedRecipe(cousin, group, "Soup");
        recipeService.create(recipeForm("chili"), cousin);

        SharedRecipeService.BulkResult result = sharedRecipeService.copyAllToAccount(
                group.getId(), List.of(chili.getId(), tacos.getId(), mine.getId()), cousin);

        assertEquals(2, result.done());
        assertEquals(1, result.renamed().size(), "the clashing chili was copied under a new name");
        assertEquals(1, result.skipped().size(), "the cook's own soup was left alone");
        List<String> names = recipeService.findAll(cousin).stream().map(Recipe::getName).toList();
        assertTrue(names.contains("Chili (Cook grp-bulkcopy)"));
        assertTrue(names.contains("Tacos"));
        assertEquals(1, ingredientRepository.findAllByUserOrderByNameAsc(cousin).stream()
                .filter(i -> i.getName().equalsIgnoreCase("Cumin")).count(),
                "both recipes need cumin, and it was created once");
    }

    @Test
    void bulkUnshareTakesOutOnlyWhatTheMemberMayTakeOut() {
        User owner = cook("grp-bulkout@example.com");
        User member = cook("grp-bulkout-m@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        join(group, owner, member, GroupRole.MEMBER);
        Recipe theirs = sharedRecipe(owner, group, "Stew");
        Recipe soup = sharedRecipe(member, group, "Soup");
        Recipe pie = sharedRecipe(member, group, "Pie");

        SharedRecipeService.BulkResult byMember = sharedRecipeService.unshareAll(
                group.getId(), List.of(theirs.getId(), soup.getId(), pie.getId()), member);
        assertEquals(2, byMember.done());
        assertEquals(1, byMember.skipped().size());
        assertEquals(List.of("Stew"), sharedIn(group).stream().map(Recipe::getName).toList());

        SharedRecipeService.BulkResult byOwner = sharedRecipeService.unshareAll(
                group.getId(), List.of(theirs.getId()), owner);
        assertEquals(1, byOwner.done());
        assertTrue(sharedIn(group).isEmpty());
    }

    @Test
    void bulkActionsAreRefusedToOutsiders() {
        User owner = cook("grp-bulkx@example.com");
        User stranger = cook("grp-bulkx-s@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        Recipe stew = sharedRecipe(owner, group, "Stew");

        assertThrows(NoSuchElementException.class,
                () -> sharedRecipeService.copyAllToAccount(group.getId(), List.of(stew.getId()), stranger));
        assertThrows(NoSuchElementException.class,
                () -> sharedRecipeService.unshareAll(group.getId(), List.of(stew.getId()), stranger));
    }

    @Test
    void theSameRecipeCannotBeCopiedTwiceUntilItChanges() {
        User owner = cook("grp-once@example.com");
        User cousin = cook("grp-once-c@example.com");
        KitchenGroup family = groupService.create("Family", null, owner);
        KitchenGroup friends = groupService.create("Friends", null, cousin);
        join(family, owner, cousin, GroupRole.MEMBER);
        join(friends, cousin, owner, GroupRole.MEMBER);
        RecipeForm chiliForm = recipeForm("Chili", ingredient(owner, "Cumin"));
        chiliForm.setGroupIds(Set.of(family.getId(), friends.getId()));
        Recipe chili = recipeService.create(chiliForm, owner);

        Recipe copy = sharedRecipeService.copyToAccount(family.getId(), chili.getId(), "Aunt's chili", cousin);
        assertTrue(copy.getCopiedFromHash().matches("[0-9a-f]{64}"));

        assertThrows(GroupRuleException.class,
                () -> sharedRecipeService.copyToAccount(family.getId(), chili.getId(), "Chili again", cousin),
                "a new name does not make it a new recipe");
        assertThrows(GroupRuleException.class,
                () -> sharedRecipeService.copyToAccount(friends.getId(), chili.getId(), "Chili thrice", cousin),
                "nor does finding it in another group");
        SharedRecipeService.BulkResult bulk = sharedRecipeService.copyAllToAccount(
                family.getId(), List.of(chili.getId()), cousin);
        assertEquals(0, bulk.done());
        assertEquals(List.of("Chili is already in your recipes"), bulk.skipped());
        assertEquals(Set.of(chili.getId()), sharedRecipeService.alreadyCopied(family, cousin));

        RecipeForm edit = RecipeForm.from(recipeService.requireOwned(chili.getId(), owner));
        edit.setInstructions("Now with beans.");
        edit.removeBlankLines();
        recipeService.update(chili.getId(), edit, owner);

        assertTrue(sharedRecipeService.alreadyCopied(family, cousin).isEmpty(), "the new version is not copied yet");
        Recipe second = sharedRecipeService.copyToAccount(family.getId(), chili.getId(), "Chili with beans", cousin);
        assertNotEquals(copy.getCopiedFromHash(), second.getCopiedFromHash());
    }

    @Test
    void deletingACopyAllowsCopyingAgain() {
        User owner = cook("grp-recopy@example.com");
        User cousin = cook("grp-recopy-c@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        join(group, owner, cousin, GroupRole.MEMBER);
        Recipe chili = sharedRecipe(owner, group, "Chili");

        Recipe copy = sharedRecipeService.copyToAccount(group.getId(), chili.getId(), null, cousin);
        recipeService.delete(copy.getId(), cousin);

        sharedRecipeService.copyToAccount(group.getId(), chili.getId(), null, cousin);
        assertEquals(1, recipeService.findAll(cousin).size());
    }

    @Test
    void theDatabaseHoldsOneCopyPerFingerprintPerAccount() {
        User user = cook("grp-onecopy@example.com");
        String fingerprint = "a".repeat(64);
        Recipe first = new Recipe(user, "Chili", 4);
        first.setCopiedFromHash(fingerprint);
        recipeRepository.saveAndFlush(first);

        Recipe second = new Recipe(user, "Chili (Sam)", 4);
        second.setCopiedFromHash(fingerprint);
        assertThrows(DataIntegrityViolationException.class, () -> recipeRepository.saveAndFlush(second));
    }

    // --------------------------------------------------------------- removing

    @Test
    void deletingAGroupLeavesEveryonesRecipesAlone() {
        User owner = cook("grp-del@example.com");
        User member = cook("grp-del-m@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        join(group, owner, member, GroupRole.MEMBER);
        Recipe soup = sharedRecipe(member, group, "Soup");

        groupService.delete(group.getId(), owner);

        assertFalse(groupRepository.existsById(group.getId()));
        assertTrue(recipeRepository.findById(soup.getId()).isPresent());
        assertTrue(groupService.membershipsOf(member).isEmpty());
    }

    @Test
    void deletingAnAccountTakesItsGroupButNotOtherPeoplesRecipes() {
        User owner = cook("grp-acct@example.com");
        User member = cook("grp-acct-m@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        join(group, owner, member, GroupRole.MEMBER);
        Recipe soup = sharedRecipe(member, group, "Soup");
        sharedRecipe(owner, group, "Stew", ingredient(owner, "Beef"));
        entityManager.flush();

        accountDeletionService.delete(owner);

        assertFalse(groupRepository.existsById(group.getId()));
        assertTrue(recipeRepository.findById(soup.getId()).isPresent());
    }

    // ------------------------------------------------------------ uniqueness

    @Test
    void recipeAndIngredientNamesAreUniquePerAccountIgnoringCaseInTheDatabase() {
        User user = cook("grp-unique@example.com");
        User other = cook("grp-unique-o@example.com");
        recipeRepository.saveAndFlush(new Recipe(user, "Soup", 2));
        recipeRepository.saveAndFlush(new Recipe(other, "SOUP", 2));

        assertThrows(DataIntegrityViolationException.class,
                () -> recipeRepository.saveAndFlush(new Recipe(user, "SOUP", 2)));
    }

    @Test
    void ingredientNamesAreUniquePerAccountIgnoringCaseInTheDatabase() {
        User user = cook("grp-unique-i@example.com");
        ingredientRepository.saveAndFlush(new Ingredient(user, "Butter", IngredientCategory.DAIRY));

        assertThrows(DataIntegrityViolationException.class,
                () -> ingredientRepository.saveAndFlush(new Ingredient(user, "butter", IngredientCategory.DAIRY)));
    }
}
