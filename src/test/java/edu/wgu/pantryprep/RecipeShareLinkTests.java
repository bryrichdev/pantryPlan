package edu.wgu.pantryprep;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.IngredientCategory;
import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.RecipePhoto;
import edu.wgu.pantryprep.domain.StorageLocation;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.photos.RecipePhotoService;
import edu.wgu.pantryprep.photos.RecipePhotoService.Upload;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.IngredientService;
import edu.wgu.pantryprep.service.RecipeService;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.IngredientForm;
import edu.wgu.pantryprep.web.form.RecipeForm;
import edu.wgu.pantryprep.web.form.RecipeLineForm;
import edu.wgu.pantryprep.web.form.RegistrationForm;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Public recipe links: making them, what visitors see, and saving a copy. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecipeShareLinkTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private RecipeService recipeService;
    @Autowired private IngredientService ingredientService;
    @Autowired private RecipePhotoService photoService;

    @Test
    void theOwnerMakesALinkAndAnyoneCanReadTheRecipe() throws Exception {
        User owner = cook("Sam");
        Recipe chili = chili(owner);
        assertNull(chili.getShareToken());
        mockMvc.perform(get("/recipes/{id}", chili.getId()).with(user(new AppUserDetails(owner))))
                .andExpect(content().string(containsString("Create link")));

        mockMvc.perform(post("/recipes/{id}/share", chili.getId()).with(user(new AppUserDetails(owner))).with(csrf()))
                .andExpect(redirectedUrl("/recipes/" + chili.getId() + "#share"));
        String token = token(chili, owner);
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"), token);

        mockMvc.perform(get("/recipes/{id}", chili.getId()).with(user(new AppUserDetails(owner))))
                .andExpect(content().string(containsString("value=\"http://localhost/r/" + token + "\"")))
                .andExpect(content().string(containsString("data-copy=\"share-url\"")));

        Long photo = photoService.photosOf(chili).getFirst().getId();
        mockMvc.perform(get("/r/{t}", token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
                .andExpect(content().string(containsString("<h1>Weeknight chili</h1>")))
                .andExpect(content().string(containsString("Ground beef")))
                .andExpect(content().string(containsString("Brown the beef")))
                .andExpect(content().string(containsString("/r/" + token + "/photos/" + photo)))
                .andExpect(content().string(containsString("Sign in to save")))
                .andExpect(content().string(containsString("Create a free account")))
                .andExpect(content().string(not(containsString(owner.getEmail()))))
                .andExpect(content().string(not(containsString("Times cooked"))))
                .andExpect(content().string(not(containsString("data-photo-upload"))));
        mockMvc.perform(get("/r/{t}/photos/{p}", token, photo).param("size", "thumb"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"));
    }

    @Test
    void aNewLinkRetiresTheOldOneAndStoppingEndsIt() throws Exception {
        User owner = cook("Sam");
        Recipe chili = chili(owner);
        mockMvc.perform(post("/recipes/{id}/share", chili.getId()).with(user(new AppUserDetails(owner))).with(csrf()));
        String first = token(chili, owner);
        mockMvc.perform(post("/recipes/{id}/share", chili.getId()).with(user(new AppUserDetails(owner))).with(csrf()));
        assertEquals(first, token(chili, owner), "sharing again keeps the same link");

        mockMvc.perform(post("/recipes/{id}/share/replace", chili.getId())
                        .with(user(new AppUserDetails(owner))).with(csrf()))
                .andExpect(flash().attribute("message", containsString("old link no longer works")));
        String second = token(chili, owner);
        assertNotEquals(first, second);
        mockMvc.perform(get("/r/{t}", first))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("doesn't work anymore")));
        Long photo = photoService.photosOf(chili).getFirst().getId();
        mockMvc.perform(get("/r/{t}/photos/{p}", first, photo)).andExpect(status().isNotFound());
        mockMvc.perform(get("/r/{t}", second)).andExpect(status().isOk());

        mockMvc.perform(post("/recipes/{id}/share/stop", chili.getId())
                        .with(user(new AppUserDetails(owner))).with(csrf()))
                .andExpect(redirectedUrl("/recipes/" + chili.getId() + "#share"));
        assertNull(token(chili, owner));
        mockMvc.perform(get("/r/{t}", second)).andExpect(status().isNotFound());
    }

    @Test
    void onlyTheOwnerControlsTheLinkAndLinksCantBeGuessed() throws Exception {
        User owner = cook("Sam");
        User stranger = cook("Alex");
        Recipe chili = chili(owner);
        mockMvc.perform(post("/recipes/{id}/share", chili.getId()).with(user(new AppUserDetails(stranger))).with(csrf()))
                .andExpect(status().isNotFound());
        assertNull(token(chili, owner));
        mockMvc.perform(post("/recipes/{id}/share", chili.getId()).with(user(new AppUserDetails(owner))).with(csrf()));
        mockMvc.perform(post("/recipes/{id}/share/stop", chili.getId()).with(user(new AppUserDetails(stranger))).with(csrf()))
                .andExpect(status().isNotFound());
        assertNotNull(token(chili, owner));

        mockMvc.perform(get("/r/{t}", "not-a-real-token")).andExpect(status().isNotFound());
        mockMvc.perform(get("/r/{t}", "A".repeat(43))).andExpect(status().isNotFound());
        mockMvc.perform(get("/r/{t}/photos/{p}", "A".repeat(43), 1)).andExpect(status().isNotFound());
    }

    @Test
    void aSignedInCookCanSaveACopyOnce() throws Exception {
        User owner = cook("Sam");
        User visitor = cook("Alex");
        Recipe chili = chili(owner);
        mockMvc.perform(post("/recipes/{id}/share", chili.getId()).with(user(new AppUserDetails(owner))).with(csrf()));
        String token = token(chili, owner);

        mockMvc.perform(get("/r/{t}/save", token)).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")));
        mockMvc.perform(post("/r/{t}/save", token).with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")));
        mockMvc.perform(get("/r/{t}/save", token).with(user(new AppUserDetails(visitor))))
                .andExpect(redirectedUrl("/r/" + token));

        mockMvc.perform(get("/r/{t}", token).with(user(new AppUserDetails(visitor))))
                .andExpect(content().string(containsString("Save to my recipes")));
        mockMvc.perform(get("/r/{t}", token).with(user(new AppUserDetails(owner))))
                .andExpect(content().string(containsString("Open in your recipes")))
                .andExpect(content().string(not(containsString("Save to my recipes"))));

        mockMvc.perform(post("/r/{t}/save", token).with(user(new AppUserDetails(visitor))).with(csrf()))
                .andExpect(redirectedUrlPattern("/recipes/*"))
                .andExpect(flash().attribute("message", "Saved to your recipes. It's yours to edit."));
        List<Recipe> mine = recipeService.findAll(visitor);
        assertEquals(1, mine.size());
        Recipe copy = recipeService.requireOwned(mine.getFirst().getId(), visitor);
        assertEquals("Weeknight chili", copy.getName());
        assertEquals(1, copy.getLines().size());
        assertEquals("https://www.seriouseats.com/chili", copy.getSourceUrl());
        assertNull(copy.getShareToken(), "a copy isn't shared just because its original is");
        List<RecipePhoto> photos = photoService.photosOf(copy);
        assertEquals(1, photos.size());

        mockMvc.perform(post("/r/{t}/save", token).with(user(new AppUserDetails(visitor))).with(csrf()))
                .andExpect(redirectedUrl("/r/" + token))
                .andExpect(flash().attribute("error", containsString("already saved")));
        assertEquals(1, recipeService.findAll(visitor).size());
    }

    private String token(Recipe recipe, User owner) {
        return recipeService.requireOwned(recipe.getId(), owner).getShareToken();
    }

    private Recipe chili(User owner) {
        IngredientForm ingredientForm = new IngredientForm();
        ingredientForm.setName("Ground beef");
        ingredientForm.setCategory(IngredientCategory.MEAT);
        ingredientForm.setStockUnit(Unit.POUND);
        ingredientForm.setDefaultLocation(StorageLocation.FRIDGE);
        Ingredient beef = ingredientService.create(ingredientForm, owner);

        RecipeForm form = new RecipeForm();
        form.setName("Weeknight chili");
        form.setServings(6);
        form.setInstructions("Brown the beef. Simmer.");
        form.setSourceUrl("https://www.seriouseats.com/chili");
        RecipeLineForm line = new RecipeLineForm();
        line.setIngredientId(beef.getId());
        line.setQuantity(new BigDecimal("1"));
        line.setUnit(Unit.POUND);
        form.getLines().add(line);
        Recipe recipe = recipeService.create(form, owner);
        photoService.add(recipe.getId(), List.of(new Upload("a.jpg", TestPhotos.jpeg(80, 60))), owner);
        return recipe;
    }

    private User cook(String name) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName(name);
        form.setEmail("share-" + UUID.randomUUID() + "@example.com");
        form.setPassword("correcthorsebattery");
        form.setConfirmPassword("correcthorsebattery");
        return userService.register(form);
    }
}
