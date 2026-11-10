package edu.wgu.pantryprep.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

/**
 * Tidies and checks a recipe's source link. People paste addresses with or
 * without "https://", so a bare "example.com/soup" is accepted and given one.
 * Only http and https addresses with a real host are kept, and never one with
 * a user name in it ("https://bank.com@evil.example"), so the link shown on a
 * recipe page goes where it appears to.
 */
public final class SourceLinks {

    public static final int MAX_LENGTH = 500;

    private SourceLinks() {
    }

    /** True for blank, or for an address {@link #normalize} accepts. */
    public static boolean isAcceptable(String raw) {
        return raw == null || raw.isBlank() || normalize(raw).isPresent();
    }

    /** The cleaned-up address, or empty when it isn't a usable web address. Blank gives empty too. */
    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String text = raw.strip();
        if (text.chars().anyMatch(Character::isWhitespace)) {
            return Optional.empty();
        }
        if (!text.matches("(?i)^[a-z][a-z0-9+.-]*:.*")) {
            text = "https://" + text.replaceFirst("^/+", "");
        }
        if (text.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(text);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost();
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return Optional.empty();
            }
            if (host == null || uri.getRawUserInfo() != null || !(host.contains(".") || host.equals("localhost"))) {
                return Optional.empty();
            }
            return Optional.of(scheme + text.substring(text.indexOf(':')));
        } catch (URISyntaxException ex) {
            return Optional.empty();
        }
    }

    /** The site's name for display: "seriouseats.com" for https://www.seriouseats.com/x. */
    public static String host(String url) {
        if (url == null) {
            return "";
        }
        try {
            String host = new URI(url).getHost();
            if (host == null) {
                return url;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (URISyntaxException ex) {
            return url;
        }
    }
}
