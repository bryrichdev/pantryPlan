package edu.wgu.pantryprep.web;

import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.photos.RecipePhotoService;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.GroupRuleException;
import edu.wgu.pantryprep.service.RecipeShareLinkService;
import edu.wgu.pantryprep.service.SharedRecipeService;
import edu.wgu.pantryprep.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.NoSuchElementException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Public recipe links: the owner's controls on the recipe page, and the
 * read-only page anyone with the link sees at /r/{token}, signed in or not.
 */
@Controller
public class RecipeShareController {

    private final RecipeShareLinkService shareLinks;
    private final SharedRecipeService sharedRecipes;
    private final RecipePhotoService photoService;
    private final UserService userService;

    public RecipeShareController(RecipeShareLinkService shareLinks, SharedRecipeService sharedRecipes,
                                 RecipePhotoService photoService, UserService userService) {
        this.shareLinks = shareLinks;
        this.sharedRecipes = sharedRecipes;
        this.photoService = photoService;
        this.userService = userService;
    }

    /** The full address of a recipe's public page, for the owner to copy. */
    public static String publicUrl(String token) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path("/r/{token}").buildAndExpand(token)
                .toUriString();
    }

    // -------------------------------------------------------- owner controls

    @PostMapping("/recipes/{id}/share")
    public String start(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id,
                        RedirectAttributes redirect) {
        shareLinks.start(id, currentUser(principal));
        redirect.addFlashAttribute("message", "Link created. Anyone with it can view this recipe.");
        return "redirect:/recipes/" + id + "#share";
    }

    @PostMapping("/recipes/{id}/share/replace")
    public String replace(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id,
                          RedirectAttributes redirect) {
        shareLinks.replace(id, currentUser(principal));
        redirect.addFlashAttribute("message", "New link made. The old link no longer works.");
        return "redirect:/recipes/" + id + "#share";
    }

    @PostMapping("/recipes/{id}/share/stop")
    public String stop(@AuthenticationPrincipal AppUserDetails principal, @PathVariable Long id,
                       RedirectAttributes redirect) {
        shareLinks.stop(id, currentUser(principal));
        redirect.addFlashAttribute("message", "Sharing stopped. The link no longer works.");
        return "redirect:/recipes/" + id + "#share";
    }

    // ---------------------------------------------------------- public page

    @GetMapping("/r/{token}")
    public String view(@PathVariable String token, @AuthenticationPrincipal AppUserDetails principal,
                       Model model, HttpServletResponse response) {
        // A stopped link should stop showing at once, and none of this belongs in search results.
        response.setHeader("Cache-Control", "private, no-cache");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
        Recipe recipe = shareLinks.find(token).orElse(null);
        if (recipe == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "recipes/public-gone";
        }
        model.addAttribute("recipe", recipe);
        model.addAttribute("token", token);
        model.addAttribute("photos", photoService.photosOf(recipe));
        model.addAttribute("ownRecipe", principal != null && recipe.getUser().getId().equals(principal.getId()));
        return "recipes/public";
    }

    @GetMapping("/r/{token}/photos/{photoId}")
    public ResponseEntity<byte[]> photo(@PathVariable String token, @PathVariable Long photoId,
                                        @RequestParam(name = "size", required = false) String size) {
        boolean thumbnail = "thumb".equals(size);
        ResponseEntity<byte[]> image = shareLinks.find(token)
                .map(recipe -> RecipePhotoController.image(
                        photoService.bytesOf(recipe.getId(), photoId, thumbnail), true))
                .orElseGet(() -> ResponseEntity.notFound().build());
        if (!image.getStatusCode().is2xxSuccessful()) {
            return image;
        }
        // Kept for an hour rather than a year, so a stopped link's photos fade from caches too.
        return ResponseEntity.ok().headers(image.getHeaders())
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePrivate())
                .header("X-Robots-Tag", "noindex")
                .body(image.getBody());
    }

    /**
     * Where "Sign in to save" leads. Signing in brings the visitor back here,
     * and this sends them on to the recipe, where the save button now shows.
     */
    @GetMapping("/r/{token}/save")
    public String afterSignIn(@PathVariable String token) {
        return "redirect:/r/" + token;
    }

    @PostMapping("/r/{token}/save")
    public String save(@PathVariable String token, @AuthenticationPrincipal AppUserDetails principal,
                       RedirectAttributes redirect) {
        Recipe source = shareLinks.find(token).orElseThrow(() -> new NoSuchElementException("No shared recipe"));
        try {
            Recipe copy = sharedRecipes.copyFromShareLink(source, currentUser(principal));
            redirect.addFlashAttribute("message", "Saved to your recipes. It's yours to edit.");
            redirect.addFlashAttribute("celebrate", true);
            return "redirect:/recipes/" + copy.getId();
        } catch (GroupRuleException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/r/" + token;
        }
    }

    private User currentUser(AppUserDetails principal) {
        return userService.requireById(principal.getId());
    }
}
