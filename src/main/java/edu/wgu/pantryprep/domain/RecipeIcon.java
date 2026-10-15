package edu.wgu.pantryprep.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small picture a recipe can wear so it is quick to spot: what the dish is,
 * or what it is cooked in. Optional, and purely for looks: it plays no part
 * in planning, shopping, or the copy fingerprint.
 *
 * <p>The drawings live in {@code templates/fragments/recipe-icons.html} and
 * the database lists the allowed names in {@code ck_recipes_icon}; a new
 * picture needs all three, plus a migration.
 */
public enum RecipeIcon {

    POT("Soup pot", Kind.COOKWARE),
    SKILLET("Skillet", Kind.COOKWARE),
    WOK("Wok", Kind.COOKWARE),
    SHEET_PAN("Sheet pan", Kind.COOKWARE),
    CASSEROLE("Baking dish", Kind.COOKWARE),
    SLOW_COOKER("Slow cooker", Kind.COOKWARE),
    GRILL("Grill", Kind.COOKWARE),
    PASTA("Pasta", Kind.DISH),
    RICE_BOWL("Rice bowl", Kind.DISH),
    SALAD("Salad", Kind.DISH),
    TACO("Taco", Kind.DISH),
    WRAP("Wrap or burrito", Kind.DISH),
    SANDWICH("Sandwich", Kind.DISH),
    PIZZA("Pizza", Kind.DISH),
    PANCAKES("Pancakes", Kind.SWEET),
    EGG("Eggs", Kind.SWEET),
    TOAST("Toast", Kind.SWEET),
    PORRIDGE("Oats or porridge", Kind.SWEET),
    PARFAIT("Yogurt or parfait", Kind.SWEET),
    SMOOTHIE("Smoothie", Kind.SWEET),
    MUFFIN("Muffin", Kind.SWEET),
    BREAD("Bread", Kind.SWEET),
    COOKIE("Cookies", Kind.SWEET),
    PIE("Pie", Kind.SWEET),
    CAKE("Cake", Kind.SWEET);

    /** The headings the picker sorts pictures under. */
    public enum Kind {
        COOKWARE("Cooked in"),
        DISH("Dishes"),
        SWEET("Breakfast and sweets");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final String label;
    private final Kind kind;

    RecipeIcon(String label, Kind kind) {
        this.label = label;
        this.kind = kind;
    }

    public String getLabel() {
        return label;
    }

    public Kind getKind() {
        return kind;
    }

    /** Every picture, grouped under its heading in picker order. */
    public static Map<Kind, List<RecipeIcon>> byKind() {
        Map<Kind, List<RecipeIcon>> groups = new LinkedHashMap<>();
        for (Kind kind : Kind.values()) {
            groups.put(kind, new ArrayList<>());
        }
        for (RecipeIcon icon : values()) {
            groups.get(icon.kind).add(icon);
        }
        return groups;
    }
}
