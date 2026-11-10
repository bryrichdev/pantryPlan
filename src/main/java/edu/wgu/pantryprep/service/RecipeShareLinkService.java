package edu.wgu.pantryprep.service;

import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.RecipeLine;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.repository.RecipeRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public links to single recipes. A link is 32 random bytes, so it can't be
 * guessed, and it shows the recipe read-only to anyone who has it, signed in
 * or not. Only the owner starts, replaces or stops sharing.
 */
@Service
public class RecipeShareLinkService {

    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RecipeRepository recipeRepository;
    private final Clock clock;

    @Autowired
    public RecipeShareLinkService(RecipeRepository recipeRepository) {
        this(recipeRepository, Clock.systemUTC());
    }

    RecipeShareLinkService(RecipeRepository recipeRepository, Clock clock) {
        this.recipeRepository = recipeRepository;
        this.clock = clock;
    }

    /** Shares the recipe, keeping its link if it already has one. */
    @Transactional
    public Recipe start(Long recipeId, User user) {
        Recipe recipe = requireOwned(recipeId, user);
        if (recipe.getShareToken() == null) {
            recipe.setShareToken(newToken(), Instant.now(clock));
        }
        return recipe;
    }

    /** Swaps in a new link. The old one stops working at once. */
    @Transactional
    public Recipe replace(Long recipeId, User user) {
        Recipe recipe = requireOwned(recipeId, user);
        recipe.setShareToken(newToken(), Instant.now(clock));
        return recipe;
    }

    @Transactional
    public void stop(Long recipeId, User user) {
        requireOwned(recipeId, user).setShareToken(null, null);
    }

    /**
     * The recipe a link points at, with everything its public page shows
     * loaded. Empty for a link that is malformed, replaced or stopped.
     */
    @Transactional(readOnly = true)
    public Optional<Recipe> find(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            return Optional.empty();
        }
        return recipeRepository.findByShareToken(token).map(recipe -> {
            recipe.getTags().size();
            for (RecipeLine line : recipe.getLines()) {
                line.getIngredient().getName();
            }
            recipe.getUser().getId();
            return recipe;
        });
    }

    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Recipe requireOwned(Long recipeId, User user) {
        return recipeRepository.findByIdAndUser(recipeId, user)
                .orElseThrow(() -> new NoSuchElementException("No recipe " + recipeId + " for this account"));
    }
}
