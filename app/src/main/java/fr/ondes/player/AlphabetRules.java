package fr.ondes.player;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Accent-insensitive alphabetical groups, independent of Android UI classes. */
public final class AlphabetRules {
    public static final String LETTERS = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private AlphabetRules() {}

    public static String sortKey(String title) {
        if (title == null) return "";
        String plain = Normalizer.normalize(title.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT);
        return plain.replace('Æ','A').replace('Œ','O');
    }

    public static char group(String title) {
        String key = sortKey(title);
        if (key.isEmpty()) return '#';
        char first = key.charAt(0);
        return first >= 'A' && first <= 'Z' ? first : '#';
    }

    /** Returns first position in the current A-Z list for each group, or -1 if empty. */
    public static int[] firstPositions(List<String> titles) {
        int[] positions = new int[LETTERS.length()];
        java.util.Arrays.fill(positions, -1);
        for (int i = 0; i < titles.size(); i++) {
            int index = LETTERS.indexOf(group(titles.get(i)));
            if (index >= 0 && positions[index] < 0) positions[index] = i;
        }
        return positions;
    }

    /** Missing letters jump forward to the next existing section, otherwise backwards. */
    public static int nearestPosition(int[] positions, char group) {
        int from = LETTERS.indexOf(group);
        if (from < 0) return -1;
        if (positions[from] >= 0) return positions[from];
        for (int i = from + 1; i < positions.length; i++) if (positions[i] >= 0) return positions[i];
        for (int i = from - 1; i >= 0; i--) if (positions[i] >= 0) return positions[i];
        return -1;
    }
}
