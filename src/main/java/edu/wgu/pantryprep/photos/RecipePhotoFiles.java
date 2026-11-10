package edu.wgu.pantryprep.photos;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The photo bytes, read and written with plain SQL so that loading a
 * {@link edu.wgu.pantryprep.domain.RecipePhoto} never pulls an image into
 * memory. Runs inside whatever transaction the caller has open.
 */
@Repository
public class RecipePhotoFiles {

    private final JdbcTemplate jdbc;

    public RecipePhotoFiles(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(long photoId, byte[] image, byte[] thumbnail) {
        jdbc.update("INSERT INTO recipe_photo_files (photo_id, image, thumbnail) VALUES (?, ?, ?)",
                photoId, image, thumbnail);
    }

    /** Copies a photo's bytes inside the database, without reading them out. */
    public void copy(long fromPhotoId, long toPhotoId) {
        jdbc.update("INSERT INTO recipe_photo_files (photo_id, image, thumbnail) "
                + "SELECT ?, image, thumbnail FROM recipe_photo_files WHERE photo_id = ?", toPhotoId, fromPhotoId);
    }

    public Optional<byte[]> read(long photoId, boolean thumbnail) {
        String column = thumbnail ? "thumbnail" : "image";
        List<byte[]> rows = jdbc.query("SELECT " + column + " FROM recipe_photo_files WHERE photo_id = ?",
                (rs, n) -> rs.getBytes(1), photoId);
        return rows.stream().findFirst();
    }

    /**
     * Whether this account may see the recipe's photos: it owns the recipe, or
     * belongs to a group the recipe is shared with. The same people who can
     * open the recipe.
     */
    public boolean canView(long recipeId, long userId) {
        Boolean allowed = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM recipes r
                    WHERE r.id = ?
                      AND (r.user_id = ?
                           OR EXISTS (SELECT 1 FROM recipe_shares s
                                      JOIN group_members m ON m.group_id = s.group_id
                                      WHERE s.recipe_id = r.id AND m.user_id = ?)))
                """, Boolean.class, recipeId, userId, userId);
        return Boolean.TRUE.equals(allowed);
    }
}
