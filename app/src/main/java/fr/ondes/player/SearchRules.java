package fr.ondes.player;

import java.text.Normalizer;
import java.util.Locale;

/** Pure-Java, accent-insensitive matching shared by music and video search. */
public final class SearchRules {
    private SearchRules() {}

    public static String normalize(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    /** Every word must occur in at least one of the title, artist or album fields. */
    public static boolean matches(String query, String title, String artist, String album) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) return true;
        String haystack = normalize(title) + " " + normalize(artist) + " " + normalize(album);
        for (String term : normalized.split("\\s+")) {
            if (!haystack.contains(term)) return false;
        }
        return true;
    }
}
