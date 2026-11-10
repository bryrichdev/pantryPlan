package edu.wgu.pantryprep;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.SourceLinks;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.RecipeService;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.RegistrationForm;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** A recipe's source link: what is accepted, how it is saved, and where it shows. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecipeSourceLinkTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private RecipeService recipeService;

    @Test
    void addressesAreTidiedAndOnlyWebAddressesAreKept() {
        assertEquals(Optional.of("https://www.seriouseats.com/chili"),
                SourceLinks.normalize("  https://www.seriouseats.com/chili "));
        assertEquals(Optional.of("https://example.com/soup"), SourceLinks.normalize("example.com/soup"));
        assertEquals(Optional.of("http://example.com"), SourceLinks.normalize("HTTP://example.com"));
        assertEquals(Optional.of("https://youtu.be/abc?t=30"), SourceLinks.normalize("youtu.be/abc?t=30"));

        assertTrue(SourceLinks.normalize("javascript:alert(1)").isEmpty());
        assertTrue(SourceLinks.normalize("ftp://example.com/file").isEmpty());
        assertTrue(SourceLinks.normalize("https://bank.com@evil.example/").isEmpty(), "no user name tricks");
        assertTrue(SourceLinks.normalize("grandma's cookbook").isEmpty());
        assertTrue(SourceLinks.normalize("https://nodot/").isEmpty());
        assertTrue(SourceLinks.normalize("https://example.com/" + "a".repeat(500)).isEmpty());
        assertTrue(SourceLinks.isAcceptable(""), "blank means no link");

        assertEquals("seriouseats.com", SourceLinks.host("https://www.seriouseats.com/chili"));
        assertEquals("youtu.be", SourceLinks.host("https://youtu.be/abc"));
    }

    @Test
    void theFormSavesTheLinkAndTheRecipePageLinksToIt() throws Exception {
        User user = cook("source-save@example.com");
        mockMvc.perform(post("/recipes").with(user(new AppUserDetails(user))).with(csrf())
                        .param("name", "Chili").param("servings", "4")
                        .param("sourceUrl", "www.seriouseats.com/chili"))
                .andExpect(status().is3xxRedirection());
        Recipe chili = recipeService.findAll(user).getFirst();
        assertEquals("https://www.seriouseats.com/chili", chili.getSourceUrl());

        mockMvc.perform(get("/recipes/{id}", chili.getId()).with(user(new AppUserDetails(user))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "href=\"https://www.seriouseats.com/chili\" target=\"_blank\" rel=\"noopener noreferrer nofollow\"")))
                .andExpect(content().string(containsString(">seriouseats.com</a>")));
        mockMvc.perform(get("/recipes/{id}/edit", chili.getId()).with(user(new AppUserDetails(user))))
                .andExpect(content().string(containsString("value=\"https://www.seriouseats.com/chili\"")));

        mockMvc.perform(post("/recipes").with(user(new AppUserDetails(user))).with(csrf())
                        .param("id", chili.getId().toString())
                        .param("name", "Chili").param("servings", "4").param("sourceUrl", ""))
                .andExpect(status().is3xxRedirection());
        assertNull(recipeService.requireOwned(chili.getId(), user).getSourceUrl(), "clearing it removes it");
        mockMvc.perform(get("/recipes/{id}", chili.getId()).with(user(new AppUserDetails(user))))
                .andExpect(content().string(not(containsString("class=\"sourcelink\""))));
    }

    @Test
    void somethingThatIsNotAWebAddressIsRefusedOnTheForm() throws Exception {
        User user = cook("source-bad@example.com");
        mockMvc.perform(post("/recipes").with(user(new AppUserDetails(user))).with(csrf())
                        .param("name", "Chili").param("servings", "4")
                        .param("sourceUrl", "javascript:alert(1)"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter a web address")));
        assertTrue(recipeService.findAll(user).isEmpty());
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
