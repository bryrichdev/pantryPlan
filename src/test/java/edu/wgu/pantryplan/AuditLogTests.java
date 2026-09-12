package edu.wgu.pantryplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.wgu.pantryplan.audit.AuditLogEntry;
import edu.wgu.pantryplan.audit.AuditLogPage;
import edu.wgu.pantryplan.domain.IngredientCategory;
import edu.wgu.pantryplan.domain.Role;
import edu.wgu.pantryplan.domain.StorageLocation;
import edu.wgu.pantryplan.domain.Unit;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.audit.AuditLogService;
import edu.wgu.pantryplan.repository.UserRepository;
import edu.wgu.pantryplan.security.AppUserDetails;
import edu.wgu.pantryplan.service.AccountDeletionService;
import edu.wgu.pantryplan.service.IngredientService;
import edu.wgu.pantryplan.service.UserService;
import edu.wgu.pantryplan.web.form.IngredientForm;
import edu.wgu.pantryplan.web.form.RegistrationForm;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The audit trail.
 *
 * <p>Unlike the other test classes, this one is deliberately not
 * {@code @Transactional}. The signed-in account is handed to the database when
 * a transaction begins. A test-wide transaction begins before any request, so
 * every change inside it would be recorded with no account. Here each request
 * gets its own transaction, as it does in the running app. The accounts made
 * are deleted again after each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuditLogTests {

    private static final String PASSWORD = "correcthorsebattery";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private IngredientService ingredientService;
    @Autowired private AccountDeletionService accountDeletionService;
    @Autowired private AuditLogService auditLogService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<Long> created = new ArrayList<>();

    private User account(String email) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName("Audited");
        form.setEmail(email);
        form.setPassword(PASSWORD);
        form.setConfirmPassword(PASSWORD);
        User user = userService.register(form);
        created.add(user.getId());
        return user;
    }

    private User admin(String email) {
        User admin = account(email);
        admin.setRole(Role.ROLE_ADMIN);
        return userRepository.save(admin);
    }

    @AfterEach
    void removeAccounts() {
        for (Long id : created) {
            userRepository.findById(id).ifPresent(accountDeletionService::delete);
        }
    }

    private List<AuditLogEntry> entries(String table, String action, String account) {
        return auditLogService.search(table, action, account, 0).getEntries();
    }

    @Test
    void recordsWhoMadeAChange() throws Exception {
        User cook = account("audit-cook@example.com");

        mockMvc.perform(post("/recipes").with(user(new AppUserDetails(cook))).with(csrf())
                        .param("name", "Audited stew")
                        .param("servings", "4"))
                .andExpect(status().is3xxRedirection());

        AuditLogEntry entry = entries("recipes", "INSERT", cook.getEmail()).getFirst();
        assertEquals("Audited stew", entry.getSummary());
        assertEquals(cook.getEmail(), entry.getActor());
        assertNull(entry.getImpersonator(), "no admin was involved");
    }

    @Test
    void namesTheAdminBehindAChangeMadeWhileViewingAsSomeone() throws Exception {
        User admin = admin("audit-admin@example.com");
        User cook = account("audit-viewed@example.com");
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/admin/impersonate")
                        .with(user(new AppUserDetails(admin))).with(csrf())
                        .param("username", cook.getEmail()))
                .andExpect(redirectedUrl("/dashboard"))
                .andReturn().getRequest().getSession();

        mockMvc.perform(post("/recipes").session(session).with(csrf())
                        .param("name", "Made by an admin")
                        .param("servings", "2"))
                .andExpect(status().is3xxRedirection());

        AuditLogEntry entry = entries("recipes", "INSERT", cook.getEmail()).getFirst();
        assertEquals(cook.getEmail(), entry.getActor(), "the change belongs to the viewed account");
        assertEquals(admin.getEmail(), entry.getImpersonator(), "and names the admin who made it");
    }

    @Test
    void neverRecordsPasswordHashesButDoesRecordAPasswordChange() throws Exception {
        User cook = account("audit-password@example.com");

        mockMvc.perform(post("/account/password").with(user(new AppUserDetails(cook))).with(csrf())
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", "an entirely new password")
                        .param("confirmPassword", "an entirely new password"))
                .andExpect(redirectedUrl("/account"));

        Integer leaks = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_log
                WHERE table_name = 'users' AND row_id = ?
                  AND (jsonb_exists(COALESCE(old_data, '{}'), 'password_hash')
                    OR jsonb_exists(COALESCE(new_data, '{}'), 'password_hash'))
                """, Integer.class, cook.getId());
        assertEquals(0, leaks, "no hash in any recorded row");

        AuditLogEntry change = entries("users", "UPDATE", cook.getEmail()).getFirst();
        assertTrue(change.getSummary().contains("password"), "the change itself is still visible");
    }

    @Test
    void keepsTheRecordOfADeletedAccountIncludingWhatCascaded() throws Exception {
        User admin = admin("audit-deleting-admin@example.com");
        User doomed = account("audit-doomed@example.com");
        IngredientForm ingredient = new IngredientForm();
        ingredient.setName("Doomed rice");
        ingredient.setCategory(IngredientCategory.PANTRY_STAPLE);
        ingredient.setStockUnit(Unit.GRAM);
        ingredient.setDefaultLocation(StorageLocation.PANTRY);
        ingredientService.create(ingredient, doomed);

        mockMvc.perform(post("/admin/users/{id}/delete", doomed.getId())
                        .with(user(new AppUserDetails(admin))).with(csrf()))
                .andExpect(redirectedUrl("/admin/users"));

        assertTrue(entries("users", "DELETE", admin.getEmail()).stream()
                        .anyMatch(entry -> doomed.getEmail().equals(entry.getSummary())),
                "the account's deletion is recorded against the admin who did it");
        assertTrue(entries("ingredients", "DELETE", admin.getEmail()).stream()
                        .anyMatch(entry -> "Doomed rice".equals(entry.getSummary())),
                "rows removed by ON DELETE CASCADE are recorded too");
    }

    @Test
    void onlyAdminsCanReadTheLogs() throws Exception {
        User cook = account("audit-reader@example.com");
        User admin = admin("audit-reading-admin@example.com");

        mockMvc.perform(get("/admin/logs").with(user(new AppUserDetails(cook))))
                .andExpect(status().isForbidden());

        String page = mockMvc.perform(get("/admin/logs").with(user(new AppUserDetails(admin)))
                        .param("table", "users")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("href=\"/admin/logs\""), "the Logs tab is in the navigation");
        assertTrue(page.contains("Accounts"), "entries render with their record type");
        assertTrue(page.contains("Per page"), "the page size choice renders");
    }

    @Test
    void anOddPageSizeOrAPagePastTheEndStillShowsTheLog() {
        account("audit-paging@example.com");

        AuditLogPage odd = auditLogService.search("", "", "", 0, 7);
        assertEquals(AuditLogService.DEFAULT_PAGE_SIZE, odd.getSize(), "unlisted sizes fall back to the default");

        AuditLogPage farAway = auditLogService.search("", "", "", 1_000_000, 25);
        assertEquals(farAway.getTotalPages() - 1, farAway.getPage(), "a page past the end becomes the last page");
        assertTrue(farAway.getTotalEntries() > 0);
        assertTrue(!farAway.getEntries().isEmpty(), "and it has entries on it");
    }

    @Test
    void clearsTheWholeLog() throws Exception {
        User admin = admin("audit-clear-admin@example.com");
        User cook = account("audit-clear-cook@example.com");

        mockMvc.perform(post("/recipes").with(user(new AppUserDetails(cook))).with(csrf())
                        .param("name", "Soon forgotten")
                        .param("servings", "2"))
                .andExpect(status().is3xxRedirection());
        assertFalse(entries("recipes", "INSERT", cook.getEmail()).isEmpty(), "the change was recorded first");

        mockMvc.perform(post("/admin/logs/clear").with(user(new AppUserDetails(admin))).with(csrf()))
                .andExpect(redirectedUrl("/admin/logs"));

        Long remaining = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class);
        assertEquals(0L, remaining, "nothing is left");
    }
}
