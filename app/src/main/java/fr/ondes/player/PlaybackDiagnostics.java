package fr.ondes.player;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.DateFormat;
import java.util.Date;

/** Small, private diagnostic journal for investigating unexpected playback pauses.
 * No track names, paths, media IDs, Bluetooth names, locations or accounts. */
public final class PlaybackDiagnostics {
    private static final String STORE = "onde_playback_diagnostics";
    private static final int CAP = 36;
    private PlaybackDiagnostics() {}

    public static void event(Context context, String reason, String detail) {
        SharedPreferences p = context.getApplicationContext()
                .getSharedPreferences(STORE, Context.MODE_PRIVATE);
        String clean = (detail == null ? "" : detail).replace('|', '/')
                .replace('\n', ' ').replace('\r', ' ');
        if (clean.length() > 90) clean = clean.substring(0, 90);
        String line = System.currentTimeMillis() + "|" + reason + "|" + clean;
        String existing = p.getString("events", "");
        String[] entries = existing == null ? new String[0] : existing.split("\n");
        StringBuilder result = new StringBuilder();
        for (int i = Math.max(0, entries.length-CAP+1); i < entries.length; i++) {
            if (entries[i].isEmpty()) continue;
            result.append(entries[i]).append('\n');
        }
        result.append(line);
        p.edit().putString("events", result.toString())
                .putString("last_event", reason).putLong("last_at", System.currentTimeMillis()).apply();
    }

    public static String report(Context context) {
        SharedPreferences p = context.getApplicationContext()
                .getSharedPreferences(STORE, Context.MODE_PRIVATE);
        String events = p.getString("events", "");
        StringBuilder text = new StringBuilder("Onde Player — Diagnostic de lecture\n\n");
        text.append("Événements récents (du plus ancien au plus récent) :\n");
        if (events == null || events.isEmpty())
            text.append("Aucun événement enregistré depuis cette version.\n");
        else for (String line : events.split("\n")) {
            String[] values = line.split("\\|",3);
            if (values.length < 2) continue;
            try {
                String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                        .format(new Date(Long.parseLong(values[0])));
                text.append(time).append(" — ").append(values[1]);
                if (values.length > 2 && !values[2].isEmpty())
                    text.append(" (").append(values[2]).append(")");
                text.append('\n');
            } catch (NumberFormatException ignored) {}
        }
        text.append("\nLes données restent sur ce téléphone et ne sont partagées que sur demande.");
        return text.toString();
    }
}
