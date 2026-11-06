package fr.ondes.player;

import java.util.ArrayList;

/** Pure frequency-to-control mapping for the simple bass/treble equalizer. */
public final class ToneRules {
    private ToneRules() { }

    public static int[] bands(int[] centersHz, int group) {
        if (centersHz == null || centersHz.length < 2) return new int[0];
        ArrayList<Integer> matches = new ArrayList<>();
        for (int i = 0; i < centersHz.length; i++) {
            if ((group == 0 && centersHz[i] > 0 && centersHz[i] <= 400)
                    || (group == 1 && centersHz[i] >= 2000)) matches.add(i);
        }
        if (matches.isEmpty()) matches.add(group == 0 ? 0 : centersHz.length - 1);
        int[] result = new int[matches.size()];
        for (int i = 0; i < result.length; i++) result[i] = matches.get(i);
        return result;
    }

    public static short gain(short minimum, short maximum, int position) {
        int progress = Math.max(0, Math.min(100, position));
        int value = progress >= 50
                ? Math.round(maximum * (progress - 50) / 50f)
                : Math.round(-minimum * (progress - 50) / 50f);
        return (short) Math.max(minimum, Math.min(maximum, value));
    }

    public static int progress(short minimum, short maximum, int level) {
        int offset = level >= 0
                ? (maximum <= 0 ? 0 : Math.round(50f * level / maximum))
                : (minimum >= 0 ? 0 : Math.round(50f * level / -minimum));
        return Math.max(0, Math.min(100, 50 + offset));
    }
}
