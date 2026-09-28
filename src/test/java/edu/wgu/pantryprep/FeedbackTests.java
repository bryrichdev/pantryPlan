package edu.wgu.pantryprep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.wgu.pantryprep.domain.Feedback;
import edu.wgu.pantryprep.domain.FeedbackKind;
import edu.wgu.pantryprep.domain.FeedbackStatus;
import edu.wgu.pantryprep.domain.Role;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.repository.FeedbackRepository;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.AccountDeletionService;
import edu.wgu.pantryprep.service.FeedbackService;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.RegistrationForm;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The report button in the corner of every page, and the admin Feedback tab
 * that reads what it sends.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeedbackTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private AccountDeletionService accountDeletionService;

    private User account(String email) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName("Cook");
        form.setEmail(email);
        form.setPassword("correcthorsebattery");
        form.setConfirmPassword("correcthorsebattery");
        return userService.register(form);
    }

    private User admin(String email) {
        User admin = account(email);
        admin.setRole(Role.ROLE_ADMIN);
        return admin;
    }

    private List<Feedback> reportsFrom(String email) {
        return feedbackRepository.findAll().stream()
                .filter(report -> report.getSenderEmail().equals(email))
                .toList();
    }

    @Test
    void theButtonAppearsOnlyWhenSignedIn() throws Exception {
        User cook = account("feedback-button@example.com");

        String dashboard = mockMvc.perform(get("/dashboard").with(user(new AppUserDetails(cook))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(dashboard.contains("id=\"feedback-dialog\""));
        assertTrue(dashboard.contains("value=\"/dashboard\""), "the dialog records the page it was sent from");

        String login = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(login.contains("feedback-dialog"), "a signed-out visitor has no account to send from");
    }

    @Test
    void aBackgroundReportIsSavedWithTheSenderAndPage() throws Exception {
        User cook = account("feedback-background@example.com");

        mockMvc.perform(post("/feedback").with(user(new AppUserDetails(cook))).with(csrf())
                        .header("X-Requested-With", "fetch")
                        .param("kind", "PROBLEM")
                        .param("summary", "  The grocery list left out eggs  ")
                        .param("details", "Planned an omelette, eggs missing.")
                        .param("pagePath", "/grocery-lists/4"))
                .andExpect(status().isNoContent());

        List<Feedback> reports = reportsFrom(cook.getEmail());
        assertEquals(1, reports.size());
        Feedback report = reports.getFirst();
        assertEquals(FeedbackKind.PROBLEM, report.getKind());
        assertEquals("The grocery list left out eggs", report.getSummary());
        assertEquals("/grocery-lists/4", report.getPagePath());
        assertEquals(FeedbackStatus.OPEN, report.getStatus());
    }

    @Test
    void anOrdinarySubmitReturnsToThePageItCameFrom() throws Exception {
        User cook = account("feedback-plain@example.com");

        mockMvc.perform(post("/feedback").with(user(new AppUserDetails(cook))).with(csrf())
                        .param("kind", "REQUEST")
                        .param("summary", "Let me sort recipes by cuisine")
                        .param("pagePath", "/recipes"))
                .andExpect(redirectedUrl("/recipes"))
                .andExpect(flash().attribute("feedbackSent", true));

        assertNull(reportsFrom(cook.getEmail()).getFirst().getDetails(), "blank details are stored as nothing");
    }

    @Test
    void aBlankSummaryIsRefusedAndNothingIsSaved() throws Exception {
        User cook = account("feedback-blank@example.com");

        String body = mockMvc.perform(post("/feedback").with(user(new AppUserDetails(cook))).with(csrf())
                        .header("X-Requested-With", "fetch")
                        .param("kind", "PROBLEM")
                        .param("summary", "   ")
                        .param("pagePath", "/pantry"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertEquals("Add a short summary", body);
        assertTrue(reportsFrom(cook.getEmail()).isEmpty());
    }

    @Test
    void anOutsideAddressIsNeitherFollowedNorStored() throws Exception {
        User cook = account("feedback-redirect@example.com");

        mockMvc.perform(post("/feedback").with(user(new AppUserDetails(cook))).with(csrf())
                        .param("kind", "PROBLEM")
                        .param("summary", "Something odd")
                        .param("pagePath", "//evil.example/steal"))
                .andExpect(redirectedUrl("/dashboard"));

        assertNull(reportsFrom(cook.getEmail()).getFirst().getPagePath());
    }

    @Test
    void onlyAdminsCanReadTheInbox() throws Exception {
        User cook = account("feedback-reader@example.com");
        User admin = admin("feedback-admin@example.com");
        feedbackService.submit(cook.getId(), FeedbackKind.REQUEST, "Dark mode please", null, "/dashboard");

        mockMvc.perform(get("/admin/feedback").with(user(new AppUserDetails(cook))))
                .andExpect(status().isForbidden());

        String inbox = mockMvc.perform(get("/admin/feedback").with(user(new AppUserDetails(admin))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(inbox.contains("Dark mode please"));
        assertTrue(inbox.contains(cook.getEmail()));
    }

    @Test
    void anAdminCanResolveAndReopenAReport() throws Exception {
        User cook = account("feedback-resolve@example.com");
        User admin = admin("feedback-resolver@example.com");
        Feedback report = feedbackService.submit(cook.getId(), FeedbackKind.PROBLEM, "Broken", null, null);

        mockMvc.perform(post("/admin/feedback/{id}/status", report.getId())
                        .with(user(new AppUserDetails(admin))).with(csrf())
                        .param("status", "RESOLVED")
                        .param("show", "open"))
                .andExpect(redirectedUrl("/admin/feedback?show=open"));
        Feedback resolved = feedbackRepository.findById(report.getId()).orElseThrow();
        assertEquals(FeedbackStatus.RESOLVED, resolved.getStatus());
        assertNotNull(resolved.getResolvedAt());

        mockMvc.perform(post("/admin/feedback/{id}/status", report.getId())
                        .with(user(new AppUserDetails(admin))).with(csrf())
                        .param("status", "OPEN")
                        .param("show", "resolved"))
                .andExpect(redirectedUrl("/admin/feedback?show=resolved"));
        Feedback reopened = feedbackRepository.findById(report.getId()).orElseThrow();
        assertEquals(FeedbackStatus.OPEN, reopened.getStatus());
        assertNull(reopened.getResolvedAt());
    }

    @Test
    void aReportOutlivesTheAccountThatSentIt() {
        User cook = account("feedback-leaver@example.com");
        Feedback report = feedbackService.submit(cook.getId(), FeedbackKind.PROBLEM, "Leaving note", null, null);

        accountDeletionService.delete(cook);

        Feedback kept = feedbackRepository.findById(report.getId()).orElseThrow();
        assertNull(kept.getUser(), "the link to the deleted account is cleared");
        assertEquals("feedback-leaver@example.com", kept.getSenderEmail());
    }
}
