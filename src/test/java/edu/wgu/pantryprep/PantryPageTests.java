package edu.wgu.pantryprep;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.wgu.pantryprep.domain.Ingredient;
import edu.wgu.pantryprep.domain.IngredientCategory;
import edu.wgu.pantryprep.domain.StorageLocation;
import edu.wgu.pantryprep.domain.Unit;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.IngredientService;
import edu.wgu.pantryprep.service.PantryService;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.IngredientForm;
import edu.wgu.pantryprep.web.form.PantryItemForm;
import edu.wgu.pantryprep.web.form.RegistrationForm;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The pantry list rendered with rows on it. An empty pantry skips the row
 * markup entirely, so a template error there only shows once something is
 * stocked.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PantryPageTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private IngredientService ingredientService;

    @Autowired
    private PantryService pantryService;

    @Test
    void aStockedPantryRendersWithKitchenAmountsAndAnEditablePlainNumber() throws Exception {
        RegistrationForm registration = new RegistrationForm();
        registration.setDisplayName("Cook");
        registration.setEmail("pantry-page@example.com");
        registration.setPassword("correcthorsebattery");
        registration.setConfirmPassword("correcthorsebattery");
        User user = userService.register(registration);

        IngredientForm beef = new IngredientForm();
        beef.setName("Ground beef");
        beef.setCategory(IngredientCategory.MEAT);
        beef.setStockUnit(Unit.POUND);
        beef.setDefaultLocation(StorageLocation.FRIDGE);
        Ingredient ingredient = ingredientService.create(beef, user);

        PantryItemForm item = new PantryItemForm();
        item.setIngredientId(ingredient.getId());
        item.setQuantity(new BigDecimal("1.5"));
        item.setLocation(StorageLocation.FRIDGE);
        pantryService.create(item, user);

        String page = mockMvc.perform(get("/pantry").with(user(new AppUserDetails(user))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(page.contains("1 lb 8 oz"), "the list reads the way a cook says it");
        assertTrue(page.contains("data-quantity=\"1.5\""), "the edit dialog gets a plain number");
        assertTrue(page.contains("data-amount-label=\"1 lb 8 oz\""), "and the friendly version for its note");
    }
}
