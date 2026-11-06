package fr.ondes.player;

import android.net.Uri;
import java.util.Locale;

/** A local audio or video MediaStore entry. Video keys occupy a separate negative range. */
public final class Track {
    public final long id;
    public final long albumId;
    public final String title;
    public final String artist;
    public final String album;
    public final long durationMs;
    public final long dateAddedSeconds;
    public final int trackNumber;
    public final Uri uri;
    public final boolean isVideo;

    public Track(long id, long albumId, String title, String artist, String album, long durationMs,
                 long dateAddedSeconds, int trackNumber, Uri uri) {
        this(id, albumId, title, artist, album, durationMs, dateAddedSeconds, trackNumber, uri, false);
    }

    public Track(long id, long albumId, String title, String artist, String album, long durationMs,
                 long dateAddedSeconds, int trackNumber, Uri uri, boolean isVideo) {
        this.id = id;
        this.albumId = albumId;
        this.title = cleaned(title, isVideo ? "Vidéo sans titre" : "Titre inconnu");
        this.artist = cleaned(artist, isVideo ? "Vidéo locale" : "Artiste inconnu");
        this.album = cleaned(album, isVideo ? "Vidéos" : "Album inconnu");
        this.durationMs = Math.max(0, durationMs);
        this.dateAddedSeconds = Math.max(0, dateAddedSeconds);
        this.trackNumber = Math.max(0, trackNumber) % 1000;
        this.uri = uri;
        this.isVideo = isVideo;
    }

    /** Never collide with real audio ids or playlist sentinel (-2). */
    public static long videoKey(long mediaStoreId) { return -1_000_000_000L - mediaStoreId; }

    private static String cleaned(String value, String fallback) {
        return value == null || value.trim().isEmpty() || value.equalsIgnoreCase("<unknown>")
                ? fallback : value.trim();
    }

    public static String time(long ms) {
        long seconds = Math.max(0L, ms / 1000L);
        if (seconds >= 3600L) return String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }
}
