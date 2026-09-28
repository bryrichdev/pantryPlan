package edu.wgu.pantryprep;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.RegistrationForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The shared layout carries what small screens need. How it looks on a phone
 * is checked in the browser; this guards against the pieces going missing.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MobileLayoutTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Test
    void signedInPagesHaveTheMenuButtonAndPhoneStylesheet() throws Exception {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName("Phone cook");
        form.setEmail("mobile-layout@example.com");
        form.setPassword("correcthorsebattery");
        form.setConfirmPassword("correcthorsebattery");
        User cook = userService.register(form);

        String page = mockMvc.perform(get("/dashboard").with(user(new AppUserDetails(cook))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(page.contains("name=\"viewport\""), "without the viewport tag phones render a shrunken desktop page");
        assertTrue(page.contains("/css/mobile.css"));
        assertTrue(page.contains("data-nav-toggle"));
        assertTrue(page.contains("aria-controls=\"site-menu\""));
    }

    @Test
    void signedOutPagesHaveNoMenuButton() throws Exception {
        String page = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(page.contains("/css/mobile.css"));
        assertFalse(page.contains("data-nav-toggle"), "there are no links to fold away before signing in");
    }
}
