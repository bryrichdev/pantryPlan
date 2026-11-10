package edu.wgu.pantryprep.photos;

import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.RecipePhoto;
import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.photos.PhotoProcessor.Processed;
import edu.wgu.pantryprep.photos.PhotoProcessor.UnusablePhoto;
import edu.wgu.pantryprep.repository.RecipePhotoRepository;
import edu.wgu.pantryprep.repository.RecipeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Photos on recipes. Only a recipe's owner adds, removes or reorders them;
 * anyone who can open the recipe can see them. The first photo is the cover.
 */
@Service
public class RecipePhotoService {

    public static final int MAX_PHOTOS = 12;
    public static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;

    /** One chosen file: its name as the cook's device gave it, and its bytes. */
    public record Upload(String name, byte[] bytes) {
    }

    /** How an upload went: how many photos were added, and why any weren't. */
    public record AddResult(int added, List<String> problems) {
    }

    private final RecipeRepository recipeRepository;
    private final RecipePhotoRepository photoRepository;
    private final RecipePhotoFiles files;
    private final TransactionTemplate transaction;

    public RecipePhotoService(RecipeRepository recipeRepository, RecipePhotoRepository photoRepository,
                              RecipePhotoFiles files, PlatformTransactionManager transactionManager) {
        this.recipeRepository = recipeRepository;
        this.photoRepository = photoRepository;
        this.files = files;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<RecipePhoto> photosOf(Recipe recipe) {
        return photoRepository.findByRecipeOrderByPositionAscIdAsc(recipe);
    }

    /**
     * Shrinks each file and adds it after the recipe's existing photos. The
     * files are processed before the database is touched, so a slow upload
     * never holds a connection. A file that can't be used is skipped and named
     * in the result; the rest are still added.
     */
    public AddResult add(Long recipeId, List<Upload> uploads, User user) {
        requireOwned(recipeId, user);
        List<String> problems = new ArrayList<>();
        List<Processed> ready = new ArrayList<>();
        for (Upload upload : uploads) {
            String name = displayName(upload.name());
            if (upload.bytes().length > MAX_UPLOAD_BYTES) {
                problems.add(name + " is larger than 20 MB.");
                continue;
            }
            try {
                ready.add(PhotoProcessor.process(upload.bytes()));
            } catch (UnusablePhoto ex) {
                problems.add(name + ": " + ex.getMessage());
            }
        }
        if (ready.isEmpty()) {
            return new AddResult(0, problems);
        }
        Integer added = transaction.execute(status -> {
            Recipe recipe = requireOwned(recipeId, user);
            List<RecipePhoto> existing = photoRepository.findByRecipeOrderByPositionAscIdAsc(recipe);
            int room = MAX_PHOTOS - existing.size();
            int position = existing.isEmpty() ? 0 : existing.getLast().getPosition() + 1;
            int count = 0;
            for (Processed photo : ready) {
                if (count >= room) {
                    break;
                }
                RecipePhoto saved = photoRepository.saveAndFlush(new RecipePhoto(recipe, position++,
                        photo.width(), photo.height(), photo.image().length));
                files.save(saved.getId(), photo.image(), photo.thumbnail());
                count++;
            }
            return count;
        });
        int done = added == null ? 0 : added;
        if (done < ready.size()) {
            int left = ready.size() - done;
            problems.add(left + (left == 1 ? " photo wasn't" : " photos weren't") + " added: a recipe holds up to "
                    + MAX_PHOTOS + " photos.");
        }
        return new AddResult(done, problems);
    }

    @Transactional
    public void delete(Long recipeId, Long photoId, User user) {
        Recipe recipe = requireOwned(recipeId, user);
        RecipePhoto photo = requirePhoto(recipe, photoId);
        photoRepository.delete(photo);
        photoRepository.flush();
        renumber(photoRepository.findByRecipeOrderByPositionAscIdAsc(recipe));
    }

    /** Moves the photo to the front, where it becomes the cover. */
    @Transactional
    public void makeCover(Long recipeId, Long photoId, User user) {
        Recipe recipe = requireOwned(recipeId, user);
        RecipePhoto cover = requirePhoto(recipe, photoId);
        List<RecipePhoto> order = new ArrayList<>(photoRepository.findByRecipeOrderByPositionAscIdAsc(recipe));
        order.remove(cover);
        order.addFirst(cover);
        renumber(order);
    }

    /**
     * A photo's bytes for someone who may see the recipe: its owner, or a
     * member of a group it is shared with. Empty otherwise, the same as when
     * there is no such photo, so ids can't be probed.
     */
    @Transactional(readOnly = true)
    public Optional<byte[]> image(Long recipeId, Long photoId, boolean thumbnail, User user) {
        if (!files.canView(recipeId, user.getId())) {
            return Optional.empty();
        }
        return bytesOf(recipeId, photoId, thumbnail);
    }

    /** A photo's bytes, for a caller that has already decided the recipe may be seen. */
    @Transactional(readOnly = true)
    public Optional<byte[]> bytesOf(Long recipeId, Long photoId, boolean thumbnail) {
        Optional<RecipePhoto> photo = photoRepository.findById(photoId)
                .filter(found -> found.getRecipe().getId().equals(recipeId));
        return photo.flatMap(found -> files.read(found.getId(), thumbnail));
    }

    /** Gives a copied recipe the same photos, copied inside the database. */
    @Transactional
    public void copyAll(Recipe source, Recipe target) {
        for (RecipePhoto photo : photoRepository.findByRecipeOrderByPositionAscIdAsc(source)) {
            RecipePhoto copy = photoRepository.saveAndFlush(new RecipePhoto(target, photo.getPosition(),
                    photo.getWidth(), photo.getHeight(), photo.getByteSize()));
            files.copy(photo.getId(), copy.getId());
        }
    }

    private Recipe requireOwned(Long recipeId, User user) {
        return recipeRepository.findByIdAndUser(recipeId, user)
                .orElseThrow(() -> new NoSuchElementException("No recipe " + recipeId + " for this account"));
    }

    private RecipePhoto requirePhoto(Recipe recipe, Long photoId) {
        return photoRepository.findByIdAndRecipe(photoId, recipe)
                .orElseThrow(() -> new NoSuchElementException("No photo " + photoId + " on this recipe"));
    }

    private static void renumber(List<RecipePhoto> photos) {
        for (int i = 0; i < photos.size(); i++) {
            if (photos.get(i).getPosition() != i) {
                photos.get(i).setPosition(i);
            }
        }
    }

    static String displayName(String name) {
        if (name == null || name.isBlank()) {
            return "A photo";
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        return base.length() > 60 ? base.substring(0, 57) + "..." : base;
    }
}
