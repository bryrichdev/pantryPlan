package edu.wgu.pantryprep;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.RecipeIcon;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Recipe pictures through the full web stack: the picker and where they show. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecipePicturePageTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private RecipeService recipeService;

    private User cook(String email) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName("Cook");
        form.setEmail(email);
        form.setPassword("correcthorsebattery");
        form.setConfirmPassword("correcthorsebattery");
        return userService.register(form);
    }

    private Recipe recipe(User user, String name, RecipeIcon icon) {
        RecipeForm form = new RecipeForm();
        form.setName(name);
        form.setServings(4);
        form.setIcon(icon);
        return recipeService.create(form, user);
    }

    @Test
    void theFormOffersEveryPictureAndNone() throws Exception {
        User user = cook("pic-form@example.com");
        String page = mockMvc.perform(get("/recipes/new").with(user(new AppUserDetails(user))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"icon-none\"")))
                .andReturn().getResponse().getContentAsString();
        for (RecipeIcon icon : RecipeIcon.values()) {
            org.junit.jupiter.api.Assertions.assertTrue(page.contains("id=\"icon-" + icon.name() + "\""),
                    icon.name() + " is offered");
        }
    }

    @Test
    void theEditFormStartsOnTheSavedPicture() throws Exception {
        User user = cook("pic-edit@example.com");
        Recipe chili = recipe(user, "Chili", RecipeIcon.POT);
        mockMvc.perform(get("/recipes/{id}/edit", chili.getId()).with(user(new AppUserDetails(user))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"icon-POT\" checked=\"checked\"")));
    }

    @Test
    void picturesShowOnTheRecipePageAndInTheList() throws Exception {
        User user = cook("pic-show@example.com");
        Recipe chili = recipe(user, "Chili", RecipeIcon.POT);
        Recipe plain = recipe(user, "Plain toast", null);

        mockMvc.perform(get("/recipes/{id}", chili.getId()).with(user(new AppUserDetails(user))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"ricon ricon--lg is-live\"")));
        mockMvc.perform(get("/recipes/{id}", plain.getId()).with(user(new AppUserDetails(user))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("class=\"ricon"))));
        mockMvc.perform(get("/recipes").with(user(new AppUserDetails(user))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"ricon ricon--sm\"")));
    }

    @Test
    void anUnknownPictureIsRefusedOnTheForm() throws Exception {
        User user = cook("pic-bad@example.com");
        mockMvc.perform(post("/recipes").with(user(new AppUserDetails(user))).with(csrf())
                        .param("name", "Chili").param("servings", "4").param("icon", "SPACESHIP"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Choose one of the pictures")));
    }
}
