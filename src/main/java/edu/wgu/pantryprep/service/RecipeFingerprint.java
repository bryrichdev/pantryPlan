package edu.wgu.pantryprep.service;

import edu.wgu.pantryprep.domain.Recipe;
import edu.wgu.pantryprep.domain.RecipeLine;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * A SHA-256 fingerprint of what a recipe says, used to stop the same recipe
 * being copied into one account twice.
 *
 * <p>Only the content counts: name, description, meal type, cuisine,
 * servings, times, instructions, tags, and each ingredient line by
 * ingredient name, amount, unit, and note. Ids, owners, dates, and cooking
 * history do not, so the same recipe shared into two groups has one
 * fingerprint. Text is trimmed and names and tags are lower-cased, amounts
 * lose trailing zeros, and tags and lines are sorted, so cosmetic
 * differences do not count as a new recipe.
 *
 * <p>Every field is written with its length in front, so no choice of text
 * can make two different recipes run together into the same input.
 *
 * <p>The recipe's tags and its lines' ingredients must be loadable, so call
 * this inside a transaction.
 */
public final class RecipeFingerprint {

    private RecipeFingerprint() {
    }

    public static String of(Recipe recipe) {
        StringBuilder text = new StringBuilder();
        field(text, lower(recipe.getName()));
        field(text, trim(recipe.getDescription()));
        field(text, trim(recipe.getMealType()));
        field(text, trim(recipe.getNationality()));
        field(text, String.valueOf(recipe.getServings()));
        field(text, String.valueOf(recipe.getPrepMinutes()));
        field(text, String.valueOf(recipe.getCookMinutes()));
        field(text, trim(recipe.getInstructions()));

        List<String> tags = new ArrayList<>();
        recipe.getTags().forEach(tag -> tags.add(lower(tag)));
        tags.sort(null);
        field(text, String.valueOf(tags.size()));
        tags.forEach(tag -> field(text, tag));

        List<String> lines = new ArrayList<>();
        for (RecipeLine line : recipe.getLines()) {
            StringBuilder one = new StringBuilder();
            field(one, lower(line.getIngredient().getName()));
            field(one, amount(line.getQuantity()));
            field(one, line.getUnit() == null ? "" : line.getUnit().name());
            field(one, trim(line.getNote()));
            lines.add(one.toString());
        }
        lines.sort(null);
        field(text, String.valueOf(lines.size()));
        lines.forEach(line -> field(text, line));

        return sha256(text.toString());
    }

    private static void field(StringBuilder text, String value) {
        text.append(value.length()).append(':').append(value).append(';');
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String lower(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private static String amount(BigDecimal quantity) {
        if (quantity == null) {
            return "";
        }
        BigDecimal stripped = quantity.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0).toPlainString() : stripped.toPlainString();
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is always available", ex);
        }
    }
}
