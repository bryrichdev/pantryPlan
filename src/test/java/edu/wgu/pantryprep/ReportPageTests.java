package edu.wgu.pantryprep;

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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportPageTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Test
    void reportDirectoryAndEveryReportRenderForTheCurrentAccount() throws Exception {
        User owner = cook("report-pages@example.com");
        AppUserDetails principal = new AppUserDetails(owner);

        String directory = page("/reports", principal);
        assertTrue(directory.contains("Pantry stock report"));
        assertTrue(directory.contains("Recipe usage report"));
        assertTrue(directory.contains("Near-expired pantry items"));
        assertTrue(directory.contains("Expired pantry items"));

        assertTrue(page("/reports/pantry-stock", principal).contains("Pantry stock report"));
        assertTrue(page("/reports/recipe-usage", principal).contains("Recipe usage report"));
        assertTrue(page("/reports/near-expired-pantry-items", principal)
                .contains("Near-expired pantry items"));
        assertTrue(page("/reports/expired-pantry-items", principal).contains("Expired pantry items"));
    }

    private String page(String path, AppUserDetails principal) throws Exception {
        return mockMvc.perform(get(path).with(user(principal)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private User cook(String email) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName("Cook");
        form.setEmail(email);
        form.setPassword("correcthorsebattery");
        form.setConfirmPassword("correcthorsebattery");
        return userService.register(form);
    }
}
