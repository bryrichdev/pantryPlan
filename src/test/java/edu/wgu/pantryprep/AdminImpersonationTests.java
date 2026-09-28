package edu.wgu.pantryprep;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.wgu.pantryprep.domain.Role;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.RecipeService;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.RecipeForm;
import edu.wgu.pantryprep.web.form.RegistrationForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * The admin tab and "view as". These go through the real security filter
 * chain, since the access rules live there rather than in a controller.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminImpersonationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private RecipeService recipeService;

    private User account(String email, String name) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName(name);
        form.setEmail(email);
        form.setPassword("correcthorsebattery");
        form.setConfirmPassword("correcthorsebattery");
        return userService.register(form);
    }

    private User admin(String email) {
        User admin = account(email, "Admin");
        admin.setRole(Role.ROLE_ADMIN);
        return admin;
    }

    /** Starts viewing as the target and returns the session that now holds it. */
    private MockHttpSession viewAs(User admin, User target) throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/impersonate")
                        .with(user(new AppUserDetails(admin)))
                        .with(csrf())
                        .param("username", target.getEmail()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    @Test
    void anOrdinaryUserCannotOpenTheAdminTab() throws Exception {
        User cook = account("admin-plain@example.com", "Plain cook");

        mockMvc.perform(get("/admin/users").with(user(new AppUserDetails(cook))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anOrdinaryUserCannotViewAsSomeoneElse() throws Exception {
        User cook = account("admin-sneaky@example.com", "Sneaky cook");
        User victim = account("admin-victim@example.com", "Victim");

        mockMvc.perform(post("/admin/impersonate")
                        .with(user(new AppUserDetails(cook)))
                        .with(csrf())
                        .param("username", victim.getEmail()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminSeesEveryAccountWithTheAdminTab() throws Exception {
        User admin = admin("admin-list@example.com");
        account("admin-listed@example.com", "Listed cook");

        String page = mockMvc.perform(get("/admin/users").with(user(new AppUserDetails(admin))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(page.contains("admin-listed@example.com"));
        assertTrue(page.contains("View as"));
        assertTrue(page.contains("href=\"/admin/users\""), "the Admin tab is in the navigation");
    }

    @Test
    void viewingAsAUserShowsTheirDataAndABannerBack() throws Exception {
        User admin = admin("admin-viewer@example.com");
        User cook = account("admin-viewed@example.com", "Viewed cook");
        RecipeForm recipe = new RecipeForm();
        recipe.setName("Viewed cook's stew");
        recipe.setServings(4);
        recipeService.create(recipe, cook);

        MockHttpSession session = viewAs(admin, cook);

        String recipes = mockMvc.perform(get("/recipes").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(recipes.contains("stew"), "the page shows the other account's recipes");
        assertTrue(recipes.contains("Return to admin"), "the banner offers a way back");
        assertFalse(recipes.contains("href=\"/admin/users\""),
                "the Admin tab is hidden while viewing as a user");

        mockMvc.perform(get("/admin/users").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void returningRestoresTheAdminsOwnAccount() throws Exception {
        User admin = admin("admin-return@example.com");
        User cook = account("admin-returned@example.com", "Returned cook");
        MockHttpSession session = viewAs(admin, cook);

        mockMvc.perform(post("/admin/impersonate/exit").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        mockMvc.perform(get("/admin/users").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void anAdminCannotViewAsAnotherAdmin() throws Exception {
        User admin = admin("admin-one@example.com");
        User otherAdmin = admin("admin-two@example.com");

        mockMvc.perform(post("/admin/impersonate")
                        .with(user(new AppUserDetails(admin)))
                        .with(csrf())
                        .param("username", otherAdmin.getEmail()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users?switchFailed"));
    }
}
