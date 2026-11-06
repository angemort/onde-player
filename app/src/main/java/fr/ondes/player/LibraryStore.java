package fr.ondes.player;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Calendar;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;

/** Playlist and favorites database: data stays on the device. */
public final class LibraryStore extends SQLiteOpenHelper {
    public static final class Playlist {
        public final long id;
        public final String name;
        public final int count;
        Playlist(long id, String name, int count) { this.id = id; this.name = name; this.count = count; }
    }

    public static final class Statistics {
        public final long plays, listenedMs;
        public final int uniqueTracks, streakDays;
        public final long todayMs, weekMs;
        Statistics(long plays, long listenedMs, int uniqueTracks, int streakDays, long todayMs, long weekMs) {
            this.plays = plays; this.listenedMs = listenedMs;
            this.uniqueTracks = uniqueTracks; this.streakDays = streakDays;
            this.todayMs = todayMs; this.weekMs = weekMs;
        }
    }
    public static final class DailyStat {
        public final int key;
        public final long listenedMs;
        public final int plays;
        DailyStat(int key, long listenedMs, int plays) {
            this.key = key; this.listenedMs = listenedMs; this.plays = plays;
        }
    }

    public LibraryStore(Context context) { super(context, "onde_library.db", null, 3); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL, track_id INTEGER NOT NULL, position INTEGER NOT NULL, PRIMARY KEY (playlist_id, track_id))");
        db.execSQL("CREATE INDEX playlist_tracks_order ON playlist_tracks (playlist_id, position)");
        db.execSQL("CREATE TABLE favorites (track_id INTEGER PRIMARY KEY)");
        createListeningHistory(db);
        createListeningStats(db, false);
    }
    private void createListeningHistory(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS listening_history (id INTEGER PRIMARY KEY AUTOINCREMENT, track_id INTEGER NOT NULL, played_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS history_by_track ON listening_history (track_id, played_at)");
    }

    private void createListeningStats(SQLiteDatabase db, boolean migrateHistory) {
        db.execSQL("CREATE TABLE IF NOT EXISTS track_stats (track_id INTEGER PRIMARY KEY, play_count INTEGER NOT NULL DEFAULT 0, listened_ms INTEGER NOT NULL DEFAULT 0, last_played INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS daily_stats (day_key INTEGER PRIMARY KEY, play_count INTEGER NOT NULL DEFAULT 0, listened_ms INTEGER NOT NULL DEFAULT 0)");
        if (migrateHistory) {
            // Preserve the older history's play counts. Listening time starts with V3.
            db.execSQL("INSERT OR IGNORE INTO track_stats (track_id, play_count, last_played) SELECT track_id, COUNT(*), MAX(played_at) FROM listening_history GROUP BY track_id");
        }
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createListeningHistory(db);
        if (oldVersion < 3) createListeningStats(db, true);
    }

    private static int localDay(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    /** Counts an actual playback start; keeps at most the latest 1000 entries. */
    public void recordPlay(long trackId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("track_id", trackId);
        values.put("played_at", System.currentTimeMillis());
        db.beginTransaction();
        try {
            db.insertOrThrow("listening_history", null, values);
            db.execSQL("INSERT OR IGNORE INTO track_stats(track_id) VALUES(?)", new Object[]{trackId});
            db.execSQL("UPDATE track_stats SET play_count=play_count+1, last_played=? WHERE track_id=?",
                    new Object[]{System.currentTimeMillis(), trackId});
            int today = localDay(System.currentTimeMillis());
            db.execSQL("INSERT OR IGNORE INTO daily_stats(day_key) VALUES(?)", new Object[]{today});
            db.execSQL("UPDATE daily_stats SET play_count=play_count+1 WHERE day_key=?", new Object[]{today});
            db.execSQL("DELETE FROM listening_history WHERE id NOT IN (SELECT id FROM listening_history ORDER BY id DESC LIMIT 1000)");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public List<Long> recentIds(int limit) {
        List<Long> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT track_id FROM listening_history GROUP BY track_id ORDER BY MAX(id) DESC LIMIT ?",
                new String[]{String.valueOf(Math.max(1, limit))})) {
            while (c.moveToNext()) result.add(c.getLong(0));
        }
        return result;
    }

    public List<Long> mostPlayedIds(int limit) {
        List<Long> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT track_id FROM track_stats ORDER BY play_count DESC, last_played DESC LIMIT ?",
                new String[]{String.valueOf(Math.max(1, limit))})) {
            while (c.moveToNext()) result.add(c.getLong(0));
        }
        return result;
    }

    /** Store actual elapsed playing time, not a song's theoretical duration. */
    public void recordListeningTime(long trackId, long millis) {
        if (trackId == 0 || millis <= 0) return;
        long amount = millis; // Real wall-clock playback interval, including screen-off listening.
        SQLiteDatabase db = getWritableDatabase();
        int today = localDay(System.currentTimeMillis());
        db.beginTransaction();
        try {
            db.execSQL("INSERT OR IGNORE INTO track_stats(track_id) VALUES(?)", new Object[]{trackId});
            db.execSQL("UPDATE track_stats SET listened_ms=listened_ms+? WHERE track_id=?", new Object[]{amount, trackId});
            db.execSQL("INSERT OR IGNORE INTO daily_stats(day_key) VALUES(?)", new Object[]{today});
            db.execSQL("UPDATE daily_stats SET listened_ms=listened_ms+? WHERE day_key=?", new Object[]{amount, today});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public int playCount(long trackId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT play_count FROM track_stats WHERE track_id=?", new String[]{String.valueOf(trackId)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public long listenedMs(long trackId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT listened_ms FROM track_stats WHERE track_id=?", new String[]{String.valueOf(trackId)})) {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        }
    }

    public Statistics statistics() {
        long plays = 0, time = 0, today = 0, week = 0;
        int unique = 0;
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(play_count),0), COALESCE(SUM(listened_ms),0), COUNT(*) FROM track_stats WHERE play_count>0", null)) {
            if (c.moveToFirst()) { plays=c.getLong(0); time=c.getLong(1); unique=c.getInt(2); }
        }
        int todayKey = localDay(System.currentTimeMillis());
        Calendar day = Calendar.getInstance();
        day.add(Calendar.DAY_OF_YEAR, -6);
        int weekStart = localDay(day.getTimeInMillis());
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(CASE WHEN day_key=? THEN listened_ms ELSE 0 END),0), COALESCE(SUM(listened_ms),0) FROM daily_stats WHERE day_key>=?",
                new String[]{String.valueOf(todayKey), String.valueOf(weekStart)})) {
            if (c.moveToFirst()) { today=c.getLong(0); week=c.getLong(1); }
        }
        Set<Integer> activeDays = new HashSet<>();
        try (Cursor c = db.rawQuery("SELECT day_key FROM daily_stats WHERE play_count>0 OR listened_ms>0", null)) {
            while (c.moveToNext()) activeDays.add(c.getInt(0));
        }
        int streak = 0;
        day = Calendar.getInstance();
        for (int i = 0; i < 3650; i++) {
            boolean active = activeDays.contains(localDay(day.getTimeInMillis()));
            // No plays yet today: a streak from yesterday is still current.
            if (!active && i == 0) { day.add(Calendar.DAY_OF_YEAR, -1); continue; }
            if (!active) break;
            streak++;
            day.add(Calendar.DAY_OF_YEAR, -1);
        }
        return new Statistics(plays, time, unique, streak, today, week);
    }

    public List<DailyStat> lastSevenDays() {
        List<DailyStat> days = new ArrayList<>();
        Calendar c = Calendar.getInstance(Locale.FRANCE);
        c.add(Calendar.DAY_OF_YEAR, -6);
        for (int i=0;i<7;i++) {
            int key=localDay(c.getTimeInMillis());
            long time=0; int plays=0;
            try (Cursor row=getReadableDatabase().rawQuery("SELECT listened_ms, play_count FROM daily_stats WHERE day_key=?",
                    new String[]{String.valueOf(key)})) {
                if (row.moveToFirst()) { time=row.getLong(0); plays=row.getInt(1); }
            }
            days.add(new DailyStat(key, time, plays));
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return days;
    }

    /** Batch insertion; duplicate tracks are ignored like individual playlist adds. */
    public void addManyToPlaylist(long playlistId, List<Long> ids) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long position = 0;
            try (Cursor c = db.rawQuery("SELECT COALESCE(MAX(position),-1)+1 FROM playlist_tracks WHERE playlist_id=?", new String[]{String.valueOf(playlistId)})) {
                if (c.moveToFirst()) position = c.getLong(0);
            }
            for (Long id : ids) {
                ContentValues value = new ContentValues();
                value.put("playlist_id", playlistId);
                value.put("track_id", id);
                value.put("position", position);
                if (db.insertWithOnConflict("playlist_tracks", null, value, SQLiteDatabase.CONFLICT_IGNORE) != -1) position++;
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public long createPlaylist(String name) {
        ContentValues values = new ContentValues();
        values.put("name", name.trim());
        values.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("playlists", null, values);
    }

    public void rename(long id, String name) {
        ContentValues values = new ContentValues();
        values.put("name", name.trim());
        getWritableDatabase().update("playlists", values, "id=?", new String[]{String.valueOf(id)});
    }

    public void deletePlaylist(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("playlist_tracks", "playlist_id=?", new String[]{String.valueOf(id)});
            db.delete("playlists", "id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public List<Playlist> playlists() {
        List<Playlist> result = new ArrayList<>();
        String sql = "SELECT p.id, p.name, COUNT(t.track_id) FROM playlists p LEFT JOIN playlist_tracks t ON t.playlist_id=p.id GROUP BY p.id ORDER BY p.created_at DESC";
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) result.add(new Playlist(c.getLong(0), c.getString(1), c.getInt(2)));
        }
        return result;
    }

    /** Single SQLite join for the entire library: no query per visible row. */
    public Map<Long, Integer> membershipCounts() {
        Map<Long, Integer> counts = new HashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT track_id, COUNT(*) FROM playlist_tracks GROUP BY track_id", null)) {
            while (c.moveToNext()) counts.put(c.getLong(0), c.getInt(1));
        }
        return counts;
    }

    public boolean playlistContains(long playlistId, long trackId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM playlist_tracks WHERE playlist_id=? AND track_id=? LIMIT 1",
                new String[]{String.valueOf(playlistId), String.valueOf(trackId)})) {
            return c.moveToFirst();
        }
    }

    /** Remove references to a file only AFTER MediaStore confirms actual deletion. */
    public void forgetDeletedTrack(long trackId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String[] args = {String.valueOf(trackId)};
            db.delete("playlist_tracks", "track_id=?", args);
            db.delete("favorites", "track_id=?", args);
            db.delete("listening_history", "track_id=?", args);
            // Keep aggregated listening time and play history: historic activity remains historic.
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public List<Long> playlistIds(long id) {
        List<Long> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("playlist_tracks", new String[]{"track_id"},
                "playlist_id=?", new String[]{String.valueOf(id)}, null, null, "position ASC")) {
            while (c.moveToNext()) result.add(c.getLong(0));
        }
        return result;
    }

    public void addToPlaylist(long playlistId, long trackId) {
        SQLiteDatabase db = getWritableDatabase();
        long position = 0L;
        try (Cursor c = db.rawQuery("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlist_id=?",
                new String[]{String.valueOf(playlistId)})) {
            if (c.moveToFirst()) position = c.getLong(0);
        }
        ContentValues values = new ContentValues();
        values.put("playlist_id", playlistId);
        values.put("track_id", trackId);
        values.put("position", position);
        db.insertWithOnConflict("playlist_tracks", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void removeFromPlaylist(long playlistId, long trackId) {
        getWritableDatabase().delete("playlist_tracks", "playlist_id=? AND track_id=?",
                new String[]{String.valueOf(playlistId), String.valueOf(trackId)});
    }

    public void moveInPlaylist(long playlistId, long trackId, int direction) {
        List<Long> ids = playlistIds(playlistId);
        int from = ids.indexOf(trackId);
        int to = from + direction;
        if (from < 0 || to < 0 || to >= ids.size()) return;
        java.util.Collections.swap(ids, from, to);
        persistPlaylistOrder(playlistId, ids);
    }

    /** Persist drag/drop moves by stable IDs, including when the displayed list is filtered. */
    public boolean movePlaylistTrack(long playlistId, long fromTrackId, long targetTrackId) {
        if (playlistId <= 0 || fromTrackId == targetTrackId) return false;
        List<Long> before = playlistIds(playlistId);
        if (!before.contains(fromTrackId) || !before.contains(targetTrackId)) return false;
        List<Long> reordered = PlaylistOrderRules.move(before, fromTrackId, targetTrackId);
        if (before.equals(reordered)) return false;
        persistPlaylistOrder(playlistId, reordered);
        return true;
    }

    private void persistPlaylistOrder(long playlistId, List<Long> ids) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < ids.size(); i++) {
                ContentValues values = new ContentValues();
                values.put("position", i);
                db.update("playlist_tracks", values, "playlist_id=? AND track_id=?",
                        new String[]{String.valueOf(playlistId), String.valueOf(ids.get(i))});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public void setFavorite(long trackId, boolean favorite) {
        if (favorite) {
            ContentValues values = new ContentValues();
            values.put("track_id", trackId);
            getWritableDatabase().insertWithOnConflict("favorites", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        } else getWritableDatabase().delete("favorites", "track_id=?", new String[]{String.valueOf(trackId)});
    }

    public boolean isFavorite(long trackId) {
        try (Cursor c = getReadableDatabase().query("favorites", new String[]{"track_id"},
                "track_id=?", new String[]{String.valueOf(trackId)}, null, null, null)) {
            return c.moveToFirst();
        }
    }

    public Set<Long> favoriteIds() {
        Set<Long> ids = new HashSet<>();
        try (Cursor c = getReadableDatabase().query("favorites", new String[]{"track_id"}, null, null, null, null, null)) {
            while (c.moveToNext()) ids.add(c.getLong(0));
        }
        return ids;
    }
}
