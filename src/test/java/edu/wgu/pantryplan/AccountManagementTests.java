package edu.wgu.pantryplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.wgu.pantryplan.domain.Ingredient;
import edu.wgu.pantryplan.domain.IngredientCategory;
import edu.wgu.pantryplan.domain.MealPlan;
import edu.wgu.pantryplan.domain.MealSlot;
import edu.wgu.pantryplan.domain.PlanEntry;
import edu.wgu.pantryplan.domain.Recipe;
import edu.wgu.pantryplan.domain.Role;
import edu.wgu.pantryplan.domain.StorageLocation;
import edu.wgu.pantryplan.domain.Unit;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.repository.CookLogRepository;
import edu.wgu.pantryplan.repository.GroceryListRepository;
import edu.wgu.pantryplan.repository.IngredientRepository;
import edu.wgu.pantryplan.repository.MealPlanRepository;
import edu.wgu.pantryplan.repository.PantryItemRepository;
import edu.wgu.pantryplan.repository.RecipeRepository;
import edu.wgu.pantryplan.repository.UserRepository;
import edu.wgu.pantryplan.security.AppUserDetails;
import edu.wgu.pantryplan.service.AccountDeletionService;
import edu.wgu.pantryplan.service.GroceryListService;
import edu.wgu.pantryplan.service.IngredientService;
import edu.wgu.pantryplan.service.MealPlanService;
import edu.wgu.pantryplan.service.PantryService;
import edu.wgu.pantryplan.service.RecipeService;
import edu.wgu.pantryplan.service.UserService;
import edu.wgu.pantryplan.web.form.IngredientForm;
import edu.wgu.pantryplan.web.form.MealPlanForm;
import edu.wgu.pantryplan.web.form.PantryItemForm;
import edu.wgu.pantryplan.web.form.PlanEntryForm;
import edu.wgu.pantryplan.web.form.RecipeForm;
import edu.wgu.pantryplan.web.form.RecipeLineForm;
import edu.wgu.pantryplan.web.form.RegistrationForm;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deleting accounts, from the admin tab and by their owners, and the owner's
 * own changes to name, email, and password.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountManagementTests {

    private static final String PASSWORD = "correcthorsebattery";
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private AccountDeletionService accountDeletionService;
    @Autowired private IngredientService ingredientService;
    @Autowired private RecipeService recipeService;
    @Autowired private PantryService pantryService;
    @Autowired private MealPlanService mealPlanService;
    @Autowired private GroceryListService groceryListService;
    @Autowired private UserRepository userRepository;
    @Autowired private IngredientRepository ingredientRepository;
    @Autowired private RecipeRepository recipeRepository;
    @Autowired private PantryItemRepository pantryItemRepository;
    @Autowired private MealPlanRepository mealPlanRepository;
    @Autowired private GroceryListRepository groceryListRepository;
    @Autowired private CookLogRepository cookLogRepository;

    private User account(String email) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName("Cook " + email.substring(0, email.indexOf('@')));
        form.setEmail(email);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        return userService.register(form);
    }

    private User admin(String email) {
        User admin = account(email);
        admin.setRole(Role.ROLE_ADMIN);
        return userRepository.saveAndFlush(admin);
    }

    /**
     * An account with one of everything, including a cooked meal. The cook log
     * and the recipe line both point at the ingredient, which is exactly what
     * made a plain delete of the user row fail.
     */
    private User fullyUsedAccount(String email) {
        User owner = account(email);

        IngredientForm ingredientForm = new IngredientForm();
        ingredientForm.setName("Rice");
        ingredientForm.setCategory(IngredientCategory.PANTRY_STAPLE);
        ingredientForm.setStockUnit(Unit.GRAM);
        ingredientForm.setDefaultLocation(StorageLocation.PANTRY);
        Ingredient rice = ingredientService.create(ingredientForm, owner);

        PantryItemForm stock = new PantryItemForm();
        stock.setIngredientId(rice.getId());
        stock.setQuantity(new BigDecimal("500"));
        stock.setLocation(StorageLocation.PANTRY);
        pantryService.create(stock, owner);

        RecipeForm recipeForm = new RecipeForm();
        recipeForm.setName("Pilaf");
        recipeForm.setServings(4);
        recipeForm.setTagsCsv("weeknight");
        RecipeLineForm line = new RecipeLineForm();
        line.setIngredientId(rice.getId());
        line.setQuantity(new BigDecimal("100"));
        line.setUnit(Unit.GRAM);
        recipeForm.getLines().add(line);
        Recipe pilaf = recipeService.create(recipeForm, owner);

        MealPlanForm planForm = new MealPlanForm();
        planForm.setName("Week");
        planForm.setWeekStartDate(MONDAY);
        MealPlan week = mealPlanService.create(planForm, owner);
        PlanEntryForm entryForm = new PlanEntryForm();
        entryForm.setRecipeId(pilaf.getId());
        entryForm.setPlanDate(MONDAY);
        entryForm.setMealSlot(MealSlot.DINNER);
        entryForm.setServings(4);
        PlanEntry entry = mealPlanService.addEntry(week.getId(), entryForm, owner);
        mealPlanService.addEntry(week.getId(), entryFor(pilaf), owner);
        mealPlanService.markEntryCooked(week.getId(), entry.getId(), owner);
        groceryListService.generate(week.getId(), owner, MONDAY);
        return owner;
    }

    private PlanEntryForm entryFor(Recipe recipe) {
        PlanEntryForm form = new PlanEntryForm();
        form.setRecipeId(recipe.getId());
        form.setPlanDate(MONDAY.plusDays(1));
        form.setMealSlot(MealSlot.DINNER);
        form.setServings(8);
        return form;
    }

    private void assertEverythingGone(User owner) {
        assertFalse(userRepository.existsById(owner.getId()), "the account row is gone");
        assertTrue(ingredientRepository.findAllByUserOrderByNameAsc(owner).isEmpty(), "ingredients");
        assertEquals(0, recipeRepository.countByUser(owner), "recipes");
        assertEquals(0, pantryItemRepository.countByUser(owner), "pantry");
        assertEquals(0, mealPlanRepository.countByUser(owner), "meal plans");
        assertTrue(groceryListRepository.findAllByUserOrderByGeneratedAtDesc(owner).isEmpty(), "grocery lists");
        assertTrue(cookLogRepository.findAllByUserOrderByCookedAtDesc(owner).isEmpty(), "cook logs");
    }

    /* ----------------------------------------------------------- deletion */

    @Test
    void deletingAnAccountRemovesEverythingItOwnsAndNothingElse() {
        User doomed = fullyUsedAccount("delete-doomed@example.com");
        User bystander = fullyUsedAccount("delete-bystander@example.com");

        accountDeletionService.delete(doomed);

        assertEverythingGone(doomed);
        assertEquals(1, recipeRepository.countByUser(bystander), "another account keeps its recipes");
        assertEquals(1, ingredientRepository.findAllByUserOrderByNameAsc(bystander).size());
        assertEquals(1, cookLogRepository.findAllByUserOrderByCookedAtDesc(bystander).size());
    }

    @Test
    void anAdminBulkDeletesUsersButKeepsAdminsAndThemselves() throws Exception {
        User admin = admin("delete-admin@example.com");
        User otherAdmin = admin("delete-otheradmin@example.com");
        User first = fullyUsedAccount("delete-first@example.com");
        User second = account("delete-second@example.com");

        mockMvc.perform(post("/admin/users/bulk-delete")
                        .with(user(new AppUserDetails(admin)))
                        .with(csrf())
                        .param("ids", first.getId().toString(), second.getId().toString(),
                                otherAdmin.getId().toString(), admin.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        assertEverythingGone(first);
        assertFalse(userRepository.existsById(second.getId()));
        assertTrue(userRepository.existsById(otherAdmin.getId()), "another admin is kept");
        assertTrue(userRepository.existsById(admin.getId()), "the admin's own account is kept");
    }

    @Test
    void anAdminDeletesOneUser() throws Exception {
        User admin = admin("delete-single-admin@example.com");
        User target = account("delete-single@example.com");

        mockMvc.perform(post("/admin/users/{id}/delete", target.getId())
                        .with(user(new AppUserDetails(admin)))
                        .with(csrf()))
                .andExpect(redirectedUrl("/admin/users"));

        assertFalse(userRepository.existsById(target.getId()));
    }

    @Test
    void anOrdinaryUserCannotDeleteAccounts() throws Exception {
        User cook = account("delete-plain@example.com");
        User victim = account("delete-victim@example.com");

        mockMvc.perform(post("/admin/users/{id}/delete", victim.getId())
                        .with(user(new AppUserDetails(cook)))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertTrue(userRepository.existsById(victim.getId()));
    }

    @Test
    void aSessionForADeletedAccountIsSignedOut() throws Exception {
        User gone = account("delete-session@example.com");
        AppUserDetails staleLogin = new AppUserDetails(gone);
        accountDeletionService.delete(gone);

        mockMvc.perform(get("/recipes").with(user(staleLogin)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?removed"));
    }

    @Test
    void usersDeleteTheirOwnAccountWithTheirPassword() throws Exception {
        User owner = fullyUsedAccount("self-delete@example.com");
        AppUserDetails login = new AppUserDetails(owner);

        String refused = mockMvc.perform(post("/account/delete").with(user(login)).with(csrf())
                        .param("currentPassword", "not the password"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(refused.contains("That is not your password"));
        assertTrue(userRepository.existsById(owner.getId()));

        mockMvc.perform(post("/account/delete").with(user(login)).with(csrf())
                        .param("currentPassword", PASSWORD))
                .andExpect(redirectedUrl("/login?deleted"));
        assertEverythingGone(owner);
    }

    @Test
    void theLastAdminCannotDeleteThemselves() throws Exception {
        User onlyAdmin = admin("self-delete-admin@example.com");
        userRepository.findAll().stream()
                .filter(other -> other.getRole() == Role.ROLE_ADMIN && !other.getId().equals(onlyAdmin.getId()))
                .forEach(other -> other.setRole(Role.ROLE_USER));
        userRepository.flush();

        mockMvc.perform(post("/account/delete").with(user(new AppUserDetails(onlyAdmin))).with(csrf())
                        .param("currentPassword", PASSWORD))
                .andExpect(status().isOk());

        assertTrue(userRepository.existsById(onlyAdmin.getId()));
    }

    /* ------------------------------------------------------ profile edits */

    @Test
    void changingNameNeedsNoPasswordButChangingEmailDoes() throws Exception {
        User owner = account("profile-owner@example.com");
        AppUserDetails login = new AppUserDetails(owner);

        mockMvc.perform(post("/account/profile").with(user(login)).with(csrf())
                        .param("displayName", "Renamed cook")
                        .param("email", "profile-owner@example.com"))
                .andExpect(redirectedUrl("/account"));
        assertEquals("Renamed cook", userService.requireById(owner.getId()).getDisplayName());

        String refused = mockMvc.perform(post("/account/profile").with(user(login)).with(csrf())
                        .param("displayName", "Renamed cook")
                        .param("email", "profile-new@example.com"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(refused.contains("Enter your current password to change your email"));

        mockMvc.perform(post("/account/profile").with(user(login)).with(csrf())
                        .param("displayName", "Renamed cook")
                        .param("email", "Profile-New@Example.com")
                        .param("currentPassword", PASSWORD))
                .andExpect(redirectedUrl("/account"));
        assertEquals("profile-new@example.com", userService.requireById(owner.getId()).getEmail(),
                "the email is saved lower-cased, the same as at registration");
    }

    @Test
    void anEmailAlreadyInUseIsRefused() throws Exception {
        User owner = account("profile-taken-owner@example.com");
        account("profile-taken@example.com");

        String page = mockMvc.perform(post("/account/profile")
                        .with(user(new AppUserDetails(owner))).with(csrf())
                        .param("displayName", "Cook")
                        .param("email", "profile-taken@example.com")
                        .param("currentPassword", PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(page.contains("Another account already uses that email"));
    }

    @Test
    void changingThePasswordNeedsTheCurrentOne() throws Exception {
        User owner = account("password-owner@example.com");
        AppUserDetails login = new AppUserDetails(owner);

        String refused = mockMvc.perform(post("/account/password").with(user(login)).with(csrf())
                        .param("currentPassword", "wrong wrong wrong")
                        .param("newPassword", "a brand new password")
                        .param("confirmPassword", "a brand new password"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(refused.contains("That is not your current password"));
        assertFalse(refused.contains("a brand new password"), "typed passwords are not echoed back");

        mockMvc.perform(post("/account/password").with(user(login)).with(csrf())
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", "a brand new password")
                        .param("confirmPassword", "a brand new password"))
                .andExpect(redirectedUrl("/account"));
        assertTrue(userService.passwordMatches(userService.requireById(owner.getId()), "a brand new password"));
    }

    @Test
    void anAdminViewingAsAUserCannotChangeTheirAccount() throws Exception {
        User admin = admin("profile-viewing-admin@example.com");
        User owner = account("profile-viewed@example.com");
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/admin/impersonate")
                        .with(user(new AppUserDetails(admin))).with(csrf())
                        .param("username", owner.getEmail()))
                .andExpect(redirectedUrl("/dashboard"))
                .andReturn().getRequest().getSession();

        mockMvc.perform(post("/account/delete").session(session).with(csrf())
                        .param("currentPassword", PASSWORD))
                .andExpect(redirectedUrl("/account"));
        mockMvc.perform(post("/account/password").session(session).with(csrf())
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", "taken over by admin")
                        .param("confirmPassword", "taken over by admin"))
                .andExpect(redirectedUrl("/account"));

        assertTrue(userRepository.existsById(owner.getId()), "the account was not deleted");
        assertTrue(userService.passwordMatches(userService.requireById(owner.getId()), PASSWORD),
                "the password was not changed");
    }
}
