package edu.wgu.pantryprep;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.wgu.pantryprep.domain.GroupRole;
import edu.wgu.pantryprep.domain.KitchenGroup;
import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.RecipePhoto;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.photos.RecipePhotoService;
import edu.wgu.pantryprep.photos.RecipePhotoService.Upload;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.AccountDeletionService;
import edu.wgu.pantryprep.service.GroupService;
import edu.wgu.pantryprep.service.RecipeService;
import edu.wgu.pantryprep.service.SharedRecipeService;
import edu.wgu.pantryprep.service.UserService;
import edu.wgu.pantryprep.web.form.RecipeForm;
import edu.wgu.pantryprep.web.form.RegistrationForm;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Recipe photos through the pages: adding, showing, ordering, removing, sharing and copying. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecipePhotoTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private RecipeService recipeService;
    @Autowired private RecipePhotoService photoService;
    @Autowired private GroupService groupService;
    @Autowired private SharedRecipeService sharedRecipeService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountDeletionService accountDeletionService;
    @PersistenceContext private EntityManager entityManager;

    @Test
    void photosUploadShowOnTheRecipeAndAreServedToTheOwner() throws Exception {
        User cook = cook();
        Recipe chili = recipe(cook, "Chili", Set.of());

        mockMvc.perform(multipart("/recipes/{id}/photos", chili.getId())
                        .file(new MockMultipartFile("photos", "IMG_0001.jpg", "image/jpeg", TestPhotos.jpeg(3000, 2000)))
                        .file(new MockMultipartFile("photos", "plated.png", "image/png", TestPhotos.png(800, 1000)))
                        .with(user(new AppUserDetails(cook))).with(csrf()))
                .andExpect(redirectedUrl("/recipes/" + chili.getId() + "#photos"))
                .andExpect(flash().attribute("message", "2 photos added."));

        List<RecipePhoto> photos = photoService.photosOf(chili);
        assertEquals(2, photos.size());
        assertEquals(1600, photos.get(0).getWidth());
        assertEquals(List.of(0, 1), photos.stream().map(RecipePhoto::getPosition).toList());

        String page = mockMvc.perform(get("/recipes/{id}", chili.getId()).with(user(new AppUserDetails(cook))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String base = "/recipes/" + chili.getId() + "/photos/";
        assertTrue(page.contains("src=\"" + base + photos.get(0).getId() + "\""), "the cover is shown full size");
        assertTrue(page.contains("src=\"" + base + photos.get(1).getId() + "?size=thumb\""), "others as thumbnails");
        assertTrue(page.contains("2 of 12"));
        assertTrue(page.contains("id=\"photo-viewer\""));

        byte[] image = mockMvc.perform(get(base + photos.get(0).getId()).with(user(new AppUserDetails(cook))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", Matchers.containsString("private")))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals((byte) 0xFF, image[0]);
        assertEquals((byte) 0xD8, image[1]);
        byte[] thumb = mockMvc.perform(get(base + photos.get(0).getId()).param("size", "thumb")
                        .with(user(new AppUserDetails(cook))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertTrue(thumb.length < image.length);
    }

    @Test
    void anEmptyRecipeInvitesAPhotoAndABadFileIsExplained() throws Exception {
        User cook = cook();
        Recipe chili = recipe(cook, "Chili", Set.of());
        mockMvc.perform(get("/recipes/{id}", chili.getId()).with(user(new AppUserDetails(cook))))
                .andExpect(content().string(containsString("Add a photo of the dish")));

        mockMvc.perform(multipart("/recipes/{id}/photos", chili.getId())
                        .file(new MockMultipartFile("photos", "notes.txt", "text/plain", "hello".getBytes()))
                        .file(new MockMultipartFile("photos", "good.jpg", "image/jpeg", TestPhotos.jpeg(200, 200)))
                        .with(user(new AppUserDetails(cook))).with(csrf()))
                .andExpect(flash().attribute("message", "Photo added."))
                .andExpect(flash().attribute("error", containsString("notes.txt: This isn't a photo")));
        assertEquals(1, photoService.photosOf(chili).size(), "the good one is still added");
    }

    @Test
    void aRecipeHoldsTwelvePhotos() {
        User cook = cook();
        Recipe chili = recipe(cook, "Chili", Set.of());
        List<Upload> many = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            many.add(new Upload("p" + i + ".jpg", TestPhotos.jpeg(64, 48)));
        }
        RecipePhotoService.AddResult result = photoService.add(chili.getId(), many, cook);
        assertEquals(12, result.added());
        assertTrue(result.problems().getFirst().contains("2 photos weren't added"), result.problems().toString());
        assertEquals(12, photoService.photosOf(chili).size());
    }

    @Test
    void theCoverCanBeChangedAndRemovingAPhotoClosesTheGap() throws Exception {
        User cook = cook();
        Recipe chili = recipe(cook, "Chili", Set.of());
        photoService.add(chili.getId(), List.of(new Upload("a.jpg", TestPhotos.jpeg(60, 40)),
                new Upload("b.jpg", TestPhotos.jpeg(60, 40)), new Upload("c.jpg", TestPhotos.jpeg(60, 40))), cook);
        List<RecipePhoto> photos = photoService.photosOf(chili);
        Long a = photos.get(0).getId();
        Long b = photos.get(1).getId();
        Long c = photos.get(2).getId();

        mockMvc.perform(post("/recipes/{r}/photos/{p}/cover", chili.getId(), c)
                        .with(user(new AppUserDetails(cook))).with(csrf()))
                .andExpect(flash().attribute("message", "Cover photo changed."));
        assertEquals(List.of(c, a, b), ids(chili));

        mockMvc.perform(post("/recipes/{r}/photos/{p}/delete", chili.getId(), a)
                        .with(user(new AppUserDetails(cook))).with(csrf()))
                .andExpect(flash().attribute("message", "Photo removed."));
        assertEquals(List.of(c, b), ids(chili));
        assertEquals(List.of(0, 1), photoService.photosOf(chili).stream().map(RecipePhoto::getPosition).toList());
        assertEquals(0, count("SELECT count(*) FROM recipe_photo_files WHERE photo_id = " + a), "its bytes go too");
    }

    @Test
    void otherAccountsCannotSeeChangeOrProbePhotos() throws Exception {
        User cook = cook();
        User stranger = cook();
        Recipe chili = recipe(cook, "Chili", Set.of());
        photoService.add(chili.getId(), List.of(new Upload("a.jpg", TestPhotos.jpeg(60, 40))), cook);
        Long photo = photoService.photosOf(chili).getFirst().getId();

        mockMvc.perform(get("/recipes/{r}/photos/{p}", chili.getId(), photo).with(user(new AppUserDetails(stranger))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/recipes/{r}/photos/{p}/delete", chili.getId(), photo)
                        .with(user(new AppUserDetails(stranger))).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart("/recipes/{id}/photos", chili.getId())
                        .file(new MockMultipartFile("photos", "x.jpg", "image/jpeg", TestPhotos.jpeg(60, 40)))
                        .with(user(new AppUserDetails(stranger))).with(csrf()))
                .andExpect(status().isNotFound());
        assertEquals(1, photoService.photosOf(chili).size());

        Recipe mine = recipe(stranger, "Toast", Set.of());
        mockMvc.perform(get("/recipes/{r}/photos/{p}", mine.getId(), photo).with(user(new AppUserDetails(stranger))))
                .andExpect(status().isNotFound());
    }

    @Test
    void groupMembersSeeSharedPhotosAndACopyKeepsThem() throws Exception {
        User cook = cook();
        User member = cook();
        KitchenGroup family = groupService.create("Family", null, cook);
        groupService.addMember(family, member, GroupRole.MEMBER);
        Recipe chili = recipe(cook, "Chili", Set.of(family.getId()));
        photoService.add(chili.getId(), List.of(new Upload("a.jpg", TestPhotos.jpeg(60, 40)),
                new Upload("b.jpg", TestPhotos.jpeg(60, 40))), cook);
        Long photo = photoService.photosOf(chili).getFirst().getId();

        mockMvc.perform(get("/groups/{g}/recipes/{r}", family.getId(), chili.getId())
                        .with(user(new AppUserDetails(member))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/recipes/" + chili.getId() + "/photos/" + photo)))
                .andExpect(content().string(Matchers.not(containsString("data-photo-upload"))));
        mockMvc.perform(get("/recipes/{r}/photos/{p}", chili.getId(), photo).with(user(new AppUserDetails(member))))
                .andExpect(status().isOk());

        Recipe copy = sharedRecipeService.copyToAccount(family.getId(), chili.getId(), "My chili", member);
        List<RecipePhoto> copied = photoService.photosOf(copy);
        assertEquals(2, copied.size());
        assertTrue(copied.stream().noneMatch(p -> p.getId().equals(photo)), "the copy has its own photos");
        mockMvc.perform(get("/recipes/{r}/photos/{p}", copy.getId(), copied.getFirst().getId())
                        .with(user(new AppUserDetails(member))))
                .andExpect(status().isOk());
    }

    @Test
    void deletingTheRecipeDeletesItsPhotos() {
        User cook = cook();
        Recipe chili = recipe(cook, "Chili", Set.of());
        photoService.add(chili.getId(), List.of(new Upload("a.jpg", TestPhotos.jpeg(60, 40))), cook);
        entityManager.flush();
        entityManager.clear(); // the delete comes in a later request, with nothing loaded
        recipeService.delete(chili.getId(), cook);
        entityManager.flush();
        assertEquals(0, count("SELECT count(*) FROM recipe_photos WHERE recipe_id = " + chili.getId()));
        assertEquals(0, count("SELECT count(*) FROM recipe_photo_files"));
    }

    @Test
    void deletingTheAccountDeletesItsPhotos() {
        User cook = cook();
        Recipe chili = recipe(cook, "Chili", Set.of());
        photoService.add(chili.getId(), List.of(new Upload("a.jpg", TestPhotos.jpeg(60, 40))), cook);
        entityManager.flush();
        entityManager.clear();
        accountDeletionService.delete(userService.requireById(cook.getId()));
        entityManager.flush();
        assertEquals(0, count("SELECT count(*) FROM recipe_photos WHERE recipe_id = " + chili.getId()));
        assertEquals(0, count("SELECT count(*) FROM recipe_photo_files"));
    }

    @Test
    void photoChangesAreAuditedWithoutTheImageBytes() {
        User cook = cook();
        Recipe chili = recipe(cook, "Chili", Set.of());
        photoService.add(chili.getId(), List.of(new Upload("a.jpg", TestPhotos.jpeg(60, 40))), cook);
        assertEquals(1, count("SELECT count(*) FROM audit_log WHERE table_name = 'recipe_photos'"));
        assertEquals(0, count("SELECT count(*) FROM audit_log WHERE table_name = 'recipe_photo_files'"));
    }

    private List<Long> ids(Recipe recipe) {
        return photoService.photosOf(recipe).stream().map(RecipePhoto::getId).toList();
    }

    private int count(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }

    private Recipe recipe(User user, String name, Set<Long> groups) {
        RecipeForm form = new RecipeForm();
        form.setName(name);
        form.setServings(4);
        form.setGroupIds(groups);
        return recipeService.create(form, user);
    }

    private User cook() {
        RegistrationForm form = new RegistrationForm();
        form.setDisplayName("Cook");
        form.setEmail("photo-" + UUID.randomUUID() + "@example.com");
        form.setPassword("correcthorsebattery");
        form.setConfirmPassword("correcthorsebattery");
        return userService.register(form);
    }
}
