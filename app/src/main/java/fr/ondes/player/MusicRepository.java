package fr.ondes.player;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Build;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Offline library: unified index with stable, non-overlapping IDs for audio and video. */
public final class MusicRepository {
    private static final Map<Long, Track> INDEX = new HashMap<>();
    private static List<Track> tracks = Collections.emptyList();
    private MusicRepository() {}

    public static List<Track> scan(Context context) {
        List<Track> result = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();
        Uri audio = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] columns = {
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.TRACK
        };
        boolean includeAll = context.getSharedPreferences("onde_settings", Context.MODE_PRIVATE)
                .getBoolean("include_all_audio", false);
        String where = MediaStore.Audio.Media.DURATION + " > 0";
        if (!includeAll) where += " AND " + MediaStore.Audio.Media.IS_MUSIC + " != 0";
        if (hasAudioAccess(context)) {
            try (Cursor cursor = resolver.query(audio, columns, where, null, MediaStore.Audio.Media.TITLE + " ASC")) {
                if (cursor != null) while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    result.add(new Track(id, cursor.isNull(1) ? -1L : cursor.getLong(1),
                            cursor.getString(2), cursor.getString(3), cursor.getString(4),
                            cursor.getLong(5), cursor.getLong(6), cursor.isNull(7) ? 0 : cursor.getInt(7),
                            ContentUris.withAppendedId(audio, id)));
                }
            } catch (SecurityException ignored) { /* User can grant audio independently. */ }
        }
        if (hasVideoAccess(context)) {
            Uri video = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            String[] videoColumns = {
                    MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE,
                    MediaStore.Video.Media.DURATION, MediaStore.Video.Media.DATE_ADDED,
                    MediaStore.Video.Media.DISPLAY_NAME
            };
            try (Cursor cursor = resolver.query(video, videoColumns,
                    MediaStore.Video.Media.DURATION + " > 0", null,
                    MediaStore.Video.Media.TITLE + " ASC")) {
                if (cursor != null) while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    String title = cursor.getString(1);
                    if (title == null || title.isEmpty()) title = cursor.getString(4);
                    result.add(new Track(Track.videoKey(id), Track.videoKey(0), title, "Vidéo locale", "Vidéos",
                            cursor.getLong(2), cursor.getLong(3), 0,
                            ContentUris.withAppendedId(video, id), true));
                }
            } catch (SecurityException ignored) { /* Partial video permission: show accessible items. */ }
        }
        synchronized (MusicRepository.class) {
            INDEX.clear();
            for (Track track : result) INDEX.put(track.id, track);
            tracks = Collections.unmodifiableList(new ArrayList<>(result));
        }
        return result;
    }

    public static boolean hasAudioAccess(Context c) {
        return c.checkSelfPermission(Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
    public static boolean hasVideoAccess(Context c) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (c.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED)
                return true;
            return Build.VERSION.SDK_INT >= 34 &&
                    c.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
        }
        return c.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
    public static synchronized Track get(long id) { return INDEX.get(id); }
    public static synchronized List<Track> all() { return new ArrayList<>(tracks); }
    public static synchronized boolean isLoaded() { return !INDEX.isEmpty(); }
}
