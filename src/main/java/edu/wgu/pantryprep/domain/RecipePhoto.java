package edu.wgu.pantryprep.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A photo on a recipe: where it sits in the recipe's gallery and its size.
 * The first photo is the cover. The image bytes are in recipe_photo_files and
 * are read only when a photo is shown, never when a recipe is loaded.
 */
@Entity
@Table(name = "recipe_photos")
public class RecipePhoto extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    protected RecipePhoto() {
    }

    public RecipePhoto(Recipe recipe, int position, int width, int height, int byteSize) {
        this.recipe = recipe;
        this.position = position;
        this.width = width;
        this.height = height;
        this.byteSize = byteSize;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getByteSize() {
        return byteSize;
    }

    /** Landscape, portrait or square, for laying out the gallery. */
    public boolean isPortrait() {
        return height > width;
    }
}
