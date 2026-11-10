package edu.wgu.pantryprep;

import static edu.wgu.pantryprep.importing.ImportWorkbooks.PANTRY;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.PANTRY_WORKBOOK;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.RECIPES;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.RECIPE_INGREDIENTS;
import static edu.wgu.pantryprep.importing.ImportWorkbooks.RECIPE_WORKBOOK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.PantryService;
import edu.wgu.pantryprep.service.RecipeService;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.RegistrationForm;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Download, upload, preview, import and cancel, through the pages. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SpreadsheetImportPageTests {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private RecipeService recipeService;
    @Autowired private PantryService pantryService;

    private User user;
    private AppUserDetails principal;

    @BeforeEach
    void cook() {
        RegistrationForm registration = new RegistrationForm();
        registration.setDisplayName("Cook");
        registration.setEmail("import-page-" + UUID.randomUUID() + "@example.com");
        registration.setPassword("correcthorsebattery");
        registration.setConfirmPassword("correcthorsebattery");
        user = userService.register(registration);
        principal = new AppUserDetails(user);
    }

    @Test
    void bothListsLinkToTheirImportAndThePagesRender() throws Exception {
        for (String kind : new String[] {"recipes", "pantry"}) {
            String list = mockMvc.perform(get("/" + kind).with(user(principal)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(list.contains("href=\"/" + kind + "/import\""), kind + " links to its import");

            String page = mockMvc.perform(get("/" + kind + "/import").with(user(principal)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(page.contains("/" + kind + "/import/template"));
            assertTrue(page.contains("enctype=\"multipart/form-data\""));
            assertTrue(page.contains("What goes in each column"));
        }
    }

    @Test
    void templatesDownloadAsSpreadsheets() throws Exception {
        byte[] body = mockMvc.perform(get("/pantry/import/template").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", XLSX))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"pantryprep-pantry-template.xlsx\""))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals('P', body[0], "a zip file");
        assertEquals('K', body[1]);
    }

    @Test
    void uploadPreviewThenImport() throws Exception {
        byte[] file = ImportTestWorkbooks.of(RECIPE_WORKBOOK)
                .row(RECIPES, "Garlic noodles", "2", "5", "10", "Dinner", "Asian", "Pasta", "", "", "")
                .row(RECIPE_INGREDIENTS, "Garlic noodles", "Garlic", "4", "pc", "minced", "Produce")
                .bytes();
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(multipart("/recipes/import/preview")
                        .file(new MockMultipartFile("file", "noodles.xlsx", XLSX, file))
                        .session(session).with(user(principal)).with(csrf()))
                .andExpect(redirectedUrl("/recipes/import"));
        assertTrue(recipeService.findAll(user).isEmpty(), "a preview saves nothing");

        String page = mockMvc.perform(get("/recipes/import").session(session).with(user(principal)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("Ready to import"));
        assertTrue(page.contains("Garlic noodles"));
        assertTrue(page.contains("noodles.xlsx"));
        Matcher token = Pattern.compile("name=\"token\" value=\"([0-9a-f]+)\"").matcher(page);
        assertTrue(token.find(), "the page carries the upload's token");

        mockMvc.perform(post("/recipes/import/confirm").param("token", token.group(1))
                        .session(session).with(user(principal)).with(csrf()))
                .andExpect(redirectedUrl("/recipes"))
                .andExpect(flash().attribute("message", "Imported 1 recipe and added 1 new ingredient."));
        assertEquals(1, recipeService.findAll(user).size());
        assertNull(session.getAttribute("pantryprep.pendingImport"), "the held file is let go");

        mockMvc.perform(post("/recipes/import/confirm").param("token", token.group(1))
                        .session(session).with(user(principal)).with(csrf()))
                .andExpect(redirectedUrl("/recipes/import"))
                .andExpect(flash().attributeExists("error"));
        assertEquals(1, recipeService.findAll(user).size(), "a second click doesn't import twice");
    }

    @Test
    void problemsAreListedOnThePageAndCancelLetsGo() throws Exception {
        byte[] bad = ImportTestWorkbooks.of(PANTRY_WORKBOOK)
                .row(PANTRY, "Rice", "lots", "kg", "", "", "", "").bytes();
        MockHttpSession session = new MockHttpSession();
        var result = mockMvc.perform(multipart("/pantry/import/preview")
                        .file(new MockMultipartFile("file", "shelf.xlsx", XLSX, bad))
                        .session(session).with(user(principal)).with(csrf()))
                .andExpect(redirectedUrl("/pantry/import"))
                .andReturn();
        Object report = result.getFlashMap().get("report");
        assertNotNull(report);
        String withProblems = mockMvc.perform(get("/pantry/import").session(session).with(user(principal))
                        .flashAttrs(result.getFlashMap()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(withProblems.contains("Nothing was imported yet"));
        assertTrue(withProblems.contains("shelf.xlsx"));
        assertTrue(withProblems.contains("Amount"));

        byte[] good = ImportTestWorkbooks.of(PANTRY_WORKBOOK)
                .row(PANTRY, "Rice", "2", "kg", "", "", "", "").bytes();
        mockMvc.perform(multipart("/pantry/import/preview")
                        .file(new MockMultipartFile("file", "shelf.xlsx", XLSX, good))
                        .session(session).with(user(principal)).with(csrf()))
                .andExpect(redirectedUrl("/pantry/import"));
        assertNotNull(session.getAttribute("pantryprep.pendingImport"));
        mockMvc.perform(post("/pantry/import/cancel").session(session).with(user(principal)).with(csrf()))
                .andExpect(redirectedUrl("/pantry/import"));
        assertNull(session.getAttribute("pantryprep.pendingImport"));
        assertTrue(pantryService.findAll(user).isEmpty());
    }

    @Test
    void theWrongKindOfFileIsTurnedAwayPolitely() throws Exception {
        mockMvc.perform(multipart("/pantry/import/preview")
                        .file(new MockMultipartFile("file", "shelf.csv", "text/csv", "a,b".getBytes()))
                        .with(user(principal)).with(csrf()))
                .andExpect(redirectedUrl("/pantry/import"))
                .andExpect(flash().attribute("error",
                        org.hamcrest.Matchers.containsString("save it as an Excel workbook")));
    }
}
