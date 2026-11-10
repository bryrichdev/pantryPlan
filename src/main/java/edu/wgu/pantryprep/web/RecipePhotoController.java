package edu.wgu.pantryprep.web;

import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.photos.RecipePhotoService;
import edu.wgu.pantryprep.photos.RecipePhotoService.AddResult;
import edu.wgu.pantryprep.photos.RecipePhotoService.Upload;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.UserService;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Adding, ordering, removing and showing a recipe's photos. */
@Controller
@RequestMapping("/recipes/{recipeId}/photos")
public class RecipePhotoController {

    private final RecipePhotoService photoService;
    private final UserService userService;

    public RecipePhotoController(RecipePhotoService photoService, UserService userService) {
        this.photoService = photoService;
        this.userService = userService;
    }

    @PostMapping
    public String upload(@AuthenticationPrincipal AppUserDetails principal,
                         @PathVariable Long recipeId,
                         @RequestParam(name = "photos", required = false) List<MultipartFile> files,
                         RedirectAttributes redirect) throws IOException {
        List<Upload> uploads = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    uploads.add(new Upload(file.getOriginalFilename(), file.getBytes()));
                }
            }
        }
        if (uploads.isEmpty()) {
            redirect.addFlashAttribute("error", "Choose a photo to add first.");
            return backTo(recipeId);
        }
        AddResult result = photoService.add(recipeId, uploads, currentUser(principal));
        if (result.added() > 0) {
            redirect.addFlashAttribute("message", result.added() == 1 ? "Photo added."
                    : result.added() + " photos added.");
        }
        if (!result.problems().isEmpty()) {
            redirect.addFlashAttribute("error", String.join(" ", result.problems()));
        }
        return backTo(recipeId);
    }

    @PostMapping("/{photoId}/cover")
    public String makeCover(@AuthenticationPrincipal AppUserDetails principal,
                            @PathVariable Long recipeId, @PathVariable Long photoId,
                            RedirectAttributes redirect) {
        photoService.makeCover(recipeId, photoId, currentUser(principal));
        redirect.addFlashAttribute("message", "Cover photo changed.");
        return backTo(recipeId);
    }

    @PostMapping("/{photoId}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails principal,
                         @PathVariable Long recipeId, @PathVariable Long photoId,
                         RedirectAttributes redirect) {
        photoService.delete(recipeId, photoId, currentUser(principal));
        redirect.addFlashAttribute("message", "Photo removed.");
        return backTo(recipeId);
    }

    @GetMapping("/{photoId}")
    public ResponseEntity<byte[]> show(@AuthenticationPrincipal AppUserDetails principal,
                                       @PathVariable Long recipeId, @PathVariable Long photoId,
                                       @RequestParam(name = "size", required = false) String size) {
        boolean thumbnail = "thumb".equals(size);
        return image(photoService.image(recipeId, photoId, thumbnail, currentUser(principal)), true);
    }

    /**
     * A stored photo never changes under its id (a new photo gets a new id),
     * so browsers may keep it as long as they like.
     */
    static ResponseEntity<byte[]> image(Optional<byte[]> bytes, boolean privateCache) {
        if (bytes.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        CacheControl cache = CacheControl.maxAge(Duration.ofDays(365)).immutable();
        cache = privateCache ? cache.cachePrivate() : cache.cachePublic();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(cache)
                .body(bytes.get());
    }

    private static String backTo(Long recipeId) {
        return "redirect:/recipes/" + recipeId + "#photos";
    }

    private User currentUser(AppUserDetails principal) {
        return userService.requireById(principal.getId());
    }
}
