package edu.wgu.pantryprep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.wgu.pantryprep.domain.GroupRole;
import edu.wgu.pantryprep.domain.IngredientCategory;
import edu.wgu.pantryprep.domain.KitchenGroup;
import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.GroupInviteService;
import edu.wgu.pantryprep.service.GroupService;
import edu.wgu.pantryprep.service.IngredientService;
import edu.wgu.pantryprep.service.RecipeService;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.IngredientForm;
import edu.wgu.pantryprep.web.form.RecipeForm;
import edu.wgu.pantryprep.web.form.RecipeLineForm;
import edu.wgu.pantryprep.web.form.RegistrationForm;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The group pages through the full web stack: each page renders, and a
 * request the actor's role does not allow is refused even when sent by hand.
 * Outsiders get 404, members short of the role get 403.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GroupPageTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private GroupService groupService;
    @Autowired private GroupInviteService inviteService;
    @Autowired private RecipeService recipeService;
    @Autowired private IngredientService ingredientService;

    private User cook(String email) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName("Cook " + email.substring(0, email.indexOf('@')));
        form.setEmail(email);
        form.setPassword("correcthorsebattery");
        form.setConfirmPassword("correcthorsebattery");
        return userService.register(form);
    }

    private static AppUserDetails login(User user) {
        return new AppUserDetails(user);
    }

    private void join(KitchenGroup group, User owner, User joiner, GroupRole role) {
        inviteService.joinByLink(inviteService.createLink(group.getId(), owner, role, 7, null).token(), joiner);
    }

    private Recipe sharedChili(User owner, KitchenGroup group) {
        IngredientForm cumin = new IngredientForm();
        cumin.setName("Cumin");
        cumin.setCategory(IngredientCategory.SPICE);
        RecipeLineForm line = new RecipeLineForm();
        line.setIngredientId(ingredientService.create(cumin, owner).getId());
        line.setQuantity(new BigDecimal("1"));
        line.setUnit(Unit.TEASPOON);

        RecipeForm form = new RecipeForm();
        form.setName("Chili");
        form.setServings(4);
        form.getLines().add(line);
        form.setGroupIds(Set.of(group.getId()));
        return recipeService.create(form, owner);
    }

    @Test
    void groupPagesRenderForTheOwner() throws Exception {
        User owner = cook("gp-render@example.com");
        cook("someone@example.com");
        KitchenGroup group = groupService.create("Family", "Home cooking", owner);
        sharedChili(owner, group);
        inviteService.inviteByEmail(group.getId(), owner, "someone@example.com", GroupRole.MEMBER);
        inviteService.createLink(group.getId(), owner, GroupRole.MEMBER, 7, 3);

        String list = mockMvc.perform(get("/groups").with(user(login(owner))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(list.contains("data-row-href=\"/groups/" + group.getId() + "\""), "the whole row opens the group");
        String page = mockMvc.perform(get("/groups/{id}", group.getId()).with(user(login(owner))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(page.contains("Invite people"));
        assertTrue(page.contains("Group settings"));
        assertTrue(page.contains("Chili"));
        assertTrue(page.contains("someone@example.com"));
        assertTrue(page.contains("0 of 3"));
    }

    @Test
    void membersSeeNoManagementControls() throws Exception {
        User owner = cook("gp-member@example.com");
        User member = cook("gp-member-m@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        join(group, owner, member, GroupRole.MEMBER);

        String page = mockMvc.perform(get("/groups/{id}", group.getId()).with(user(login(member))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertFalse(page.contains("Invite people"));
        assertFalse(page.contains("Group settings"));
        assertFalse(page.contains("gp-member@example.com"), "only managers see emails");
    }

    @Test
    void outsidersGetNotFoundForTheGroupAndItsRecipes() throws Exception {
        User owner = cook("gp-out@example.com");
        User stranger = cook("gp-out-x@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        Recipe chili = sharedChili(owner, group);

        mockMvc.perform(get("/groups/{id}", group.getId()).with(user(login(stranger))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/groups/{g}/recipes/{r}", group.getId(), chili.getId()).with(user(login(stranger))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/groups/{g}/recipes/{r}/copy", group.getId(), chili.getId())
                        .with(user(login(stranger))).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/recipes/{id}", chili.getId()).with(user(login(stranger))))
                .andExpect(status().isNotFound());
    }

    @Test
    void handMadeRequestsAboveTheMembersRoleAreForbidden() throws Exception {
        User owner = cook("gp-forbid@example.com");
        User member = cook("gp-forbid-m@example.com");
        User viewer = cook("gp-forbid-v@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        join(group, owner, member, GroupRole.MEMBER);
        join(group, owner, viewer, GroupRole.VIEWER);
        Long viewerMemberId = groupService.requireMembership(group.getId(), viewer).getId();

        mockMvc.perform(post("/groups/{id}/links", group.getId()).with(user(login(member))).with(csrf())
                        .param("role", "MEMBER").param("lifetimeDays", "7"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/groups/{id}/members/{m}/remove", group.getId(), viewerMemberId)
                        .with(user(login(member))).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/groups/{id}/members/{m}/role", group.getId(), viewerMemberId)
                        .with(user(login(member))).with(csrf()).param("role", "ADMIN"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/groups/{id}/delete", group.getId()).with(user(login(member))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void stateChangesNeedACsrfToken() throws Exception {
        User owner = cook("gp-csrf@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);

        mockMvc.perform(post("/groups/{id}/delete", group.getId()).with(user(login(owner))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aViewerCanReadAndCopyASharedRecipe() throws Exception {
        User owner = cook("gp-copy@example.com");
        User viewer = cook("gp-copy-v@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        join(group, owner, viewer, GroupRole.VIEWER);
        Recipe chili = sharedChili(owner, group);

        String page = mockMvc.perform(get("/groups/{g}/recipes/{r}", group.getId(), chili.getId())
                        .with(user(login(viewer))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("Copy to my recipes"));
        assertTrue(page.contains("Cumin"));
        assertFalse(page.contains("Take out of group"), "viewers cannot unshare other people's recipes");

        mockMvc.perform(post("/groups/{g}/recipes/{r}/copy", group.getId(), chili.getId())
                        .with(user(login(viewer))).with(csrf()).param("name", "Chili"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "Copied to your recipes. It's yours to edit."));
        assertEquals(1, recipeService.findAll(viewer).size());
    }

    @Test
    void selectedRecipesCanBeCopiedInOneGo() throws Exception {
        User owner = cook("gp-bulk@example.com");
        User member = cook("gp-bulk-m@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        join(group, owner, member, GroupRole.MEMBER);
        Recipe chili = sharedChili(owner, group);

        String page = mockMvc.perform(get("/groups/{id}", group.getId()).with(user(login(member))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("data-select-all"));
        assertTrue(page.contains("/groups/" + group.getId() + "/recipes/bulk-copy"));

        mockMvc.perform(post("/groups/{id}/recipes/bulk-copy", group.getId())
                        .with(user(login(member))).with(csrf())
                        .param("ids", chili.getId().toString()))
                .andExpect(redirectedUrl("/groups/" + group.getId()))
                .andExpect(flash().attribute("message", "Copied 1 recipe to your recipes."));
        assertEquals(1, recipeService.findAll(member).size());

        mockMvc.perform(post("/groups/{id}/recipes/bulk-unshare", group.getId())
                        .with(user(login(member))).with(csrf())
                        .param("ids", chili.getId().toString()))
                .andExpect(flash().attribute("error", "Left in: Chili is not yours to take out."));
    }

    @Test
    void theRowCopyButtonCopiesOnceAndThenShowsCopied() throws Exception {
        User owner = cook("gp-rowcopy@example.com");
        User member = cook("gp-rowcopy-m@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        join(group, owner, member, GroupRole.MEMBER);
        Recipe chili = sharedChili(owner, group);

        String before = mockMvc.perform(get("/groups/{id}", group.getId()).with(user(login(member))))
                .andReturn().getResponse().getContentAsString();
        assertTrue(before.contains("/groups/" + group.getId() + "/recipes/" + chili.getId() + "/copy"));

        mockMvc.perform(post("/groups/{g}/recipes/{r}/copy", group.getId(), chili.getId())
                        .with(user(login(member))).with(csrf()))
                .andExpect(redirectedUrl("/groups/" + group.getId()))
                .andExpect(flash().attribute("message", "Copied 1 recipe to your recipes."));

        String after = mockMvc.perform(get("/groups/{id}", group.getId()).with(user(login(member))))
                .andReturn().getResponse().getContentAsString();
        assertFalse(after.contains("/groups/" + group.getId() + "/recipes/" + chili.getId() + "/copy\""),
                "the row no longer offers the copy");
        assertTrue(after.contains(">Copied<"));

        String detail = mockMvc.perform(get("/groups/{g}/recipes/{r}", group.getId(), chili.getId())
                        .with(user(login(member))))
                .andReturn().getResponse().getContentAsString();
        assertTrue(detail.contains("You already copied this recipe"));
        assertEquals(1, recipeService.findAll(member).size());
    }

    @Test
    void joiningByLinkPreviewsFirstAndJoinsOnPost() throws Exception {
        User owner = cook("gp-join@example.com");
        User guest = cook("gp-join-g@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);
        String token = inviteService.createLink(group.getId(), owner, GroupRole.MEMBER, 7, null).token();

        String preview = mockMvc.perform(get("/groups/join/{t}", token).with(user(login(guest))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(preview.contains("Join Family?"));
        assertTrue(groupService.findMembership(group.getId(), guest).isEmpty(), "looking does not join");

        mockMvc.perform(post("/groups/join/{t}", token).with(user(login(guest))).with(csrf()))
                .andExpect(redirectedUrl("/groups/" + group.getId()));
        assertEquals(GroupRole.MEMBER, groupService.requireMembership(group.getId(), guest).getRole());

        String bad = mockMvc.perform(get("/groups/join/{t}", "x".repeat(43)).with(user(login(guest))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(bad.contains("This invite link does not work"));
    }

    @Test
    void emailInvitesGoOnlyToExistingAccounts() throws Exception {
        User owner = cook("gp-invite@example.com");
        cook("gp-invite-real@example.com");
        KitchenGroup group = groupService.create("Family", null, owner);

        mockMvc.perform(post("/groups/{id}/invitations", group.getId())
                        .with(user(login(owner))).with(csrf())
                        .param("email", "GP-Invite-Real@example.com").param("role", "MEMBER"))
                .andExpect(flash().attribute("message",
                        "Invitation sent to gp-invite-real@example.com. It is waiting on their Groups page."));

        mockMvc.perform(post("/groups/{id}/invitations", group.getId())
                        .with(user(login(owner))).with(csrf())
                        .param("email", "gp-invite-ghost@example.com").param("role", "MEMBER"))
                .andExpect(flash().attribute("error",
                        "No PantryPrep account uses gp-invite-ghost@example.com. "
                                + "Ask them to sign up first, or send them an invite link."));
        assertEquals(1, inviteService.openInvitations(group).size());
    }

    @Test
    void theRecipeFormOffersOnlyGroupsTheCookCanShareTo() throws Exception {
        User owner = cook("gp-form@example.com");
        User viewer = cook("gp-form-v@example.com");
        KitchenGroup group = groupService.create("Family Circle", null, owner);
        join(group, owner, viewer, GroupRole.VIEWER);

        String ownerForm = mockMvc.perform(get("/recipes/new").with(user(login(owner))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(ownerForm.contains("Share with groups"));
        assertTrue(ownerForm.contains("Family Circle"));

        String viewerForm = mockMvc.perform(get("/recipes/new").with(user(login(viewer))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(viewerForm.contains("Share with groups"));

        String refused = mockMvc.perform(post("/recipes").with(user(login(viewer))).with(csrf())
                        .param("name", "Toast").param("servings", "1")
                        .param("groupIds", group.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(refused.contains("You can only share with groups"));
        assertTrue(recipeService.findAll(viewer).isEmpty());
    }

    @Test
    void pagesDoNotLeakTheirAddressToOtherSites() throws Exception {
        User owner = cook("gp-referrer@example.com");
        mockMvc.perform(get("/groups").with(user(login(owner))))
                .andExpect(header().string("Referrer-Policy", "same-origin"));
    }
}
