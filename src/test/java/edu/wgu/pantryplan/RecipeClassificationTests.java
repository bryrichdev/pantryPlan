package edu.wgu.pantryplan;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.wgu.pantryplan.domain.RecipeClassification;
import edu.wgu.pantryplan.domain.RecipePreset;
import edu.wgu.pantryplan.domain.User;
import edu.wgu.pantryplan.repository.RecipePresetRepository;
import edu.wgu.pantryplan.security.AppUserDetails;
import edu.wgu.pantryplan.service.UserService;
import edu.wgu.pantryplan.web.form.RegistrationForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Meal type and cuisine only take values from RecipeClassification, and a bad
 * id anywhere answers 404 rather than an error page.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecipeClassificationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecipePresetRepository recipePresetRepository;

    @Autowired
    private UserService userService;

    private User cook(String email) {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName("Cook");
        form.setEmail(email);
        form.setPassword("correcthorsebattery");
        form.setConfirmPassword("correcthorsebattery");
        return userService.register(form);
    }

    /**
     * The Java lists and the V11 CHECK constraints are kept in step by hand.
     * If the database ever allows a value the dropdown does not offer, a
     * preset imported with it would show as blank and be wiped on first edit.
     */
    @Test
    void everyPresetUsesAListedMealTypeAndCuisine() {
        for (RecipePreset preset : recipePresetRepository.findAll()) {
            assertTrue(RecipeClassification.MEAL_TYPES.contains(preset.getMealType()),
                    preset.getName() + " has meal type " + preset.getMealType());
            assertTrue(RecipeClassification.CUISINES.contains(preset.getNationality()),
                    preset.getName() + " has cuisine " + preset.getNationality());
        }
    }

    @Test
    void theFormRejectsAMealTypeOutsideTheList() throws Exception {
        User owner = cook("classify-form@example.com");

        String page = mockMvc.perform(post("/recipes")
                        .with(user(new AppUserDetails(owner)))
                        .with(csrf())
                        .param("name", "Midnight toast")
                        .param("servings", "2")
                        .param("mealType", "Dessert")
                        .param("nationality", "Atlantean"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(page.contains("Choose a meal type from the list"),
                "the form comes back with a message rather than saving");
        assertTrue(page.contains("Choose a cuisine from the list"));
    }

    @Test
    void anotherAccountsRecordIsNotFound() throws Exception {
        User stranger = cook("classify-404@example.com");

        mockMvc.perform(get("/recipes/{id}", Long.MAX_VALUE)
                        .with(user(new AppUserDetails(stranger))))
                .andExpect(status().isNotFound());
    }
}
