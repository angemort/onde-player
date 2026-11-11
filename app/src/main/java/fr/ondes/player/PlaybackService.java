package fr.ondes.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.view.Surface;
import android.media.audiofx.Equalizer;
import android.media.audiofx.BassBoost;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** Playback lives independently of the Activity: notifications and lock-screen controls work. */
public final class PlaybackService extends Service {
    public static final String ACTION_PLAY_PAUSE = "fr.ondes.player.TOGGLE";
    public static final String ACTION_NEXT = "fr.ondes.player.NEXT";
    public static final String ACTION_PREVIOUS = "fr.ondes.player.PREVIOUS";
    public static final String ACTION_STOP = "fr.ondes.player.STOP";
    private static final String CHANNEL = "onde_playback";
    private static final int NOTIFICATION_ID = 143;
    private final IBinder binder = new LocalBinder();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final List<Long> order = new ArrayList<>();
    private final List<Long> original = new ArrayList<>();
    private final List<Long> pending = new ArrayList<>();
    private final List<Long> history = new ArrayList<>();
    private final Random random = new Random();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable checkpoint = new Runnable() {
        @Override public void run() {
            if (playing && current != null) { flushListening(); save(); }
            main.postDelayed(this, 15000L);
        }
    };
    private int index = -1;
    private int repeat = 0; // 0 = off, 1 = all, 2 = one
    private boolean shuffle = false;
    private boolean playing = false;
    private boolean prepared = false;
    private boolean loading = false;
    private boolean startOnReady = false;
    private boolean isForeground = false;
    private boolean hasPublishedNotification = false;
    private boolean resumeAfterFocus = false;
    private boolean restoringPlayback = false;
    /** True after onStartCommand; a bound-only service dies when Activity unbinds. */
    private boolean hasStartedLifetime = false;
    private PowerManager.WakeLock preparationWakeLock;
    private boolean playLogged = false;
    private long listeningStartedElapsed = 0L;
    private LibraryStore listeningStore;

    /** Count real, foreground/background audible playback time in small checkpoints. */
    private void flushListening() {
        if (!playing || current == null || listeningStartedElapsed <= 0) return;
        long now = SystemClock.elapsedRealtime();
        long millis = Math.max(0L, now - listeningStartedElapsed);
        listeningStartedElapsed = now;
        if (millis >= 250L) {
            try { listeningStore.recordListeningTime(current.id, millis); }
            catch (RuntimeException ignored) { /* Audio must never stop because statistics fail. */ }
        }
    }
    private long sleepDeadlineElapsed = 0;
    private long resumePosition = 0L;
    private int consecutiveErrors = 0;
    private MediaPlayer player;
    private Equalizer effect;
    private Surface videoSurface;
    private int videoWidth = 0, videoHeight = 0;
    private boolean renderedVideoFrame = false;
    private int videoErrorSerial = 0;
    private String videoErrorMessage = "";
    private float playbackSpeed = 1f;
    private boolean videoOnlyAudio = false;
    // -1 bypass, -2 user-defined equalizer, >=0 native preset.
    private String customBands = "[]";

    private BassBoost bassBoost;
    private int eqSelection = -1;
    private boolean bassEnabled = false;
    private Track current;
    private Bitmap currentArtwork;
    private MediaSession session;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private NotificationManager notifications;
    private BroadcastReceiver becomingNoisy;
    private SharedPreferences prefs;

    public final class LocalBinder extends Binder {
        public PlaybackService service() { return PlaybackService.this; }
    }

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("onde_playback_state", MODE_PRIVATE);
        listeningStore = new LibraryStore(this);
        repeat = prefs.getInt("repeat", 0);
        shuffle = prefs.getBoolean("shuffle", false);
        eqSelection = prefs.getInt("eq_preset", -1);
        customBands = prefs.getString("eq_custom_bands", "[]");
        playbackSpeed = prefs.getFloat("playback_speed", 1f);
        bassEnabled = prefs.getBoolean("bass_boost", false);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        if (power != null) {
            preparationWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Onde:MediaPreparation");
            preparationWakeLock.setReferenceCounted(false);
        }
        notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "Lecture musicale", NotificationManager.IMPORTANCE_LOW));
        session = new MediaSession(this, "OndeSession");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { main.post(() -> resume()); }
            @Override public void onPause() { main.post(() -> pause()); }
            @Override public void onSkipToNext() { main.post(() -> next()); }
            @Override public void onSkipToPrevious() { main.post(() -> previous()); }
            @Override public void onSeekTo(long pos) { main.post(() -> seek(pos)); }
            @Override public void onStop() { main.post(() -> stopPlayback()); }
        });
        session.setActive(true);
        becomingNoisy = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction())) {
                    PlaybackDiagnostics.event(PlaybackService.this, "SORTIE_AUDIO_PERDUE", "Déconnexion casque / Bluetooth");
                    pause();
                }
            }
        };
        registerReceiver(becomingNoisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        syncSession();
        main.postDelayed(checkpoint, 15000L);
    }

    @Override public IBinder onBind(Intent intent) {
        PlaybackDiagnostics.event(this, "SERVICE_LIE", "L'interface contrôle la lecture");
        return binder;
    }

    /**
     * A restored queue can be controlled by an Activity using BIND_AUTO_CREATE.
     * That bind alone does not keep audio running after onStop()/unbindService().
     * Promote the bound player to a started service before any play operation.
     * This is idempotent and cannot restart a user-paused session on its own.
     */
    private void ensureStartedForPlayback() {
        if (hasStartedLifetime) return;
        try {
            startService(new Intent(this, PlaybackService.class));
            // onStartCommand is invoked asynchronously on the main thread after
            // this method returns; the actual started state is logged there.
            PlaybackDiagnostics.event(this, "DEMANDE_SERVICE_AUTONOME", "Lecture demandée depuis l'interface");
        } catch (RuntimeException exception) {
            PlaybackDiagnostics.event(this, "DEMARRAGE_SERVICE_REFUSE",
                    exception.getClass().getSimpleName());
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        hasStartedLifetime = true;
        String action = intent == null ? null : intent.getAction();
        PlaybackDiagnostics.event(this, "SERVICE_AUTONOME", action == null ? "Sans action" : action);
        if (intent == null) {
            // START_STICKY allows a system-killed player to reconstruct its local
            // queue and position. Do not restart a user-paused session.
            if (!prefs.getBoolean("wants_playback", false)) {
                stopSelf();
                return START_NOT_STICKY;
            }
            PlaybackDiagnostics.event(this, "REDEMARRAGE_SERVICE", "Tentative de reprise après interruption Android");
            restoringPlayback = true;
            try {
                startForeground(NOTIFICATION_ID, notification());
                isForeground = true;
                hasPublishedNotification = true;
            } catch (RuntimeException e) {
                PlaybackDiagnostics.event(this, "PREMIER_PLAN_REFUSE", e.getClass().getSimpleName());
            }
            new Thread(() -> {
                boolean loaded = MusicRepository.isLoaded();
                try {
                    if (!loaded) {
                        MusicRepository.scan(getApplicationContext());
                        loaded = MusicRepository.isLoaded();
                    }
                } catch (Exception e) {
                    PlaybackDiagnostics.event(this, "REPRISE_IMPOSSIBLE", "Bibliothèque inaccessible");
                }
                final boolean ready = loaded;
                main.post(() -> {
                    if (ready) {
                        restore();
                        if (current != null) {
                            resume();
                            PlaybackDiagnostics.event(this, "REPRISE_SERVICE", "Reprise de la file enregistrée");
                        }
                    }
                    restoringPlayback = false;
                    if (current == null) {
                        prefs.edit().putBoolean("wants_playback", false).apply();
                        if (isForeground) { stopForeground(true); isForeground = false; }
                        PlaybackDiagnostics.event(this, "REPRISE_IMPOSSIBLE", "Titre précédent indisponible");
                        stopSelf();
                    } else updateNotification();
                });
            }, "OndePlaybackRecovery").start();
            return START_STICKY;
        }
        if (current == null && (ACTION_PLAY_PAUSE.equals(action) || ACTION_NEXT.equals(action) || ACTION_PREVIOUS.equals(action))) {
            if (!MusicRepository.isLoaded()) {
                new Thread(() -> {
                    try { MusicRepository.scan(getApplicationContext()); } catch (Exception ignored) {}
                    main.post(() -> {
                        restore();
                        if (ACTION_PLAY_PAUSE.equals(action)) resume();
                        else if (ACTION_NEXT.equals(action)) next();
                        else if (ACTION_PREVIOUS.equals(action)) previous();
                    });
                }, "OndeWidgetRestore").start();
                return START_STICKY;
            }
            restore();
        }
        if (ACTION_PLAY_PAUSE.equals(action)) toggle();
        else if (ACTION_NEXT.equals(action)) next();
        else if (ACTION_PREVIOUS.equals(action)) previous();
        else if (ACTION_STOP.equals(action)) stopPlayback();
        // The app has been explicitly started by the user. Keep the service
        // eligible for restart while there is active playback intent.
        return START_STICKY;
    }

    public void listen(Runnable listener) { if (!listeners.contains(listener)) listeners.add(listener); }
    public void unlisten(Runnable listener) { listeners.remove(listener); }
    private void changed() {
        PlayerWidgetProvider.refresh(this, current, playing, currentArtwork);
        for (Runnable listener : listeners) listener.run();
    }

    public Track getCurrent() { return current; }
    public boolean isPlaying() { return playing; }
    public boolean isLoading() { return loading; }
    public boolean isShuffle() { return shuffle; }
    public int getRepeat() { return repeat; }
    public long getPosition() {
        if (player != null && prepared) {
            try { return Math.max(0, player.getCurrentPosition()); } catch (Exception ignored) {}
        }
        return resumePosition;
    }
    public long getDuration() {
        if (player != null && prepared) {
            try { return player.getDuration(); } catch (Exception ignored) {}
        }
        return current == null ? 0L : current.durationMs;
    }
    public List<Track> upcoming() {
        List<Track> tracks = new ArrayList<>();
        for (Long id : pending) { Track t = MusicRepository.get(id); if (t != null) tracks.add(t); }
        for (int i = index + 1; i < order.size(); i++) {
            Track t = MusicRepository.get(order.get(i));
            if (t != null) tracks.add(t);
        }
        return tracks;
    }
    public int priorityCount() { return pending.size(); }

    /** The Activity may attach/detach its video surface without restarting playback. */
    public void setVideoSurface(Surface surface) {
        Surface next = surface != null && surface.isValid() ? surface : null;
        if (videoSurface != next) renderedVideoFrame = false;
        videoSurface = next;
        applyVideoSurface();
    }
    public void setVideoOnlyAudio(boolean enabled) {
        videoOnlyAudio = enabled;
        applyVideoSurface(); changed();
    }
    public boolean isVideoOnlyAudio() { return videoOnlyAudio; }
    public int getVideoWidth() { return videoWidth; }
    public int getVideoHeight() { return videoHeight; }
    public boolean hasRenderedVideoFrame() { return renderedVideoFrame; }
    public int getVideoErrorSerial() { return videoErrorSerial; }
    public String getVideoErrorMessage() { return videoErrorMessage; }
    private void reportVideoError(int what, int extra) {
        if (current == null || !current.isVideo) return;
        videoErrorSerial++;
        videoErrorMessage = what == MediaPlayer.MEDIA_ERROR_SERVER_DIED
                ? "Le moteur vidéo Android s'est arrêté. Essaie de relancer le fichier."
                : "Impossible de décoder cette vidéo (erreur " + what + ", " + extra
                + "). Essaie une vidéo MP4 H.264/AAC.";
        changed();
    }
    private void applyVideoSurface() {
        if (player == null) return;
        try { player.setSurface(current != null && current.isVideo && !videoOnlyAudio ? videoSurface : null); }
        catch (IllegalStateException | IllegalArgumentException ignored) { }
    }
    public float getPlaybackSpeed() { return playbackSpeed; }
    public void setPlaybackSpeed(float speed) {
        playbackSpeed = Math.max(0.5f, Math.min(2f, speed));
        prefs.edit().putFloat("playback_speed", playbackSpeed).apply();
        if (player != null && prepared) {
            try {
                boolean wasPlaying = playing;
                player.setPlaybackParams(new PlaybackParams().setSpeed(playbackSpeed));
                if (!wasPlaying) player.pause();
            } catch (Exception ignored) { }
        }
        syncSession(); changed();
    }


    public String[] equalizerPresets() {
        if (effect == null) return new String[0];
        try {
            int count = effect.getNumberOfPresets();
            String[] names = new String[count];
            for (short i = 0; i < count; i++) names[i] = effect.getPresetName(i);
            return names;
        } catch (Exception e) { return new String[0]; }
    }
    public int getEqualizerPreset() { return eqSelection; }
    public int getEqualizerBandCount() {
        if (effect == null) return 0;
        try { return effect.getNumberOfBands(); } catch (Exception ignored) { return 0; }
    }
    public short[] getEqualizerRange() {
        if (effect == null) return new short[]{-1500, 1500};
        try { return effect.getBandLevelRange(); } catch (Exception ignored) { return new short[]{-1500, 1500}; }
    }
    public int getEqualizerFrequency(int band) {
        if (effect == null) return 0;
        try { return effect.getCenterFreq((short) band) / 1000; } catch (Exception ignored) { return 0; }
    }
    public short getEqualizerBandLevel(int band) {
        if (effect == null) return 0;
        try { return effect.getBandLevel((short) band); } catch (Exception ignored) { return 0; }
    }
    /** Maps simple bass/treble controls onto supported native equalizer bands. */
    private int[] toneBands(int group) {
        int count = getEqualizerBandCount();
        int[] centers = new int[count];
        for (int i = 0; i < count; i++) centers[i] = getEqualizerFrequency(i);
        return ToneRules.bands(centers, group);
    }
    public int getToneLevel(int group) {
        int[] bands = toneBands(group);
        if (bands.length == 0) return 50;
        short[] range = getEqualizerRange();
        int sum = 0;
        for (int band : bands)
            sum += ToneRules.progress(range[0], range[1], getEqualizerBandLevel(band));
        return Math.round((float) sum / bands.length);
    }
    public void setToneLevel(int group, int position) {
        short[] range = getEqualizerRange();
        short level = ToneRules.gain(range[0], range[1], position);
        for (int band : toneBands(group)) setCustomBandLevel(band, level);
    }

    public void setCustomBandLevel(int band, short level) {
        if (effect == null || band < 0 || band >= getEqualizerBandCount()) return;
        try {
            short[] range = effect.getBandLevelRange();
            level = (short) Math.max(range[0], Math.min(range[1], level));
            if (eqSelection != -2) {
                // Start from the currently audible preset, rather than flattening other bands.
                customBands = captureBandValues();
                eqSelection = -2;
                effect.setEnabled(true);
            }
            effect.setBandLevel((short) band, level);
            customBands = captureBandValues();
            prefs.edit().putString("eq_custom_bands", customBands).putInt("eq_preset", -2).apply();
        } catch (Exception ignored) { }
    }
    private String captureBandValues() {
        JSONArray a = new JSONArray();
        if (effect != null) for (short i = 0; i < getEqualizerBandCount(); i++) {
            try { a.put(effect.getBandLevel(i)); } catch (Exception ignored) { a.put(0); }
        }
        return a.toString();
    }
    private void applyCustomBands() {
        if (effect == null) return;
        try {
            JSONArray a = new JSONArray(customBands);
            short[] range = effect.getBandLevelRange();
            int count = getEqualizerBandCount();
            for (short i = 0; i < count; i++) {
                int level = i < a.length() ? a.optInt(i, 0) : 0;
                effect.setBandLevel(i, (short) Math.max(range[0], Math.min(range[1], level)));
            }
            effect.setEnabled(true);
        } catch (Exception ignored) { }
    }
    public void resetCustomEqualizer() {
        int count = getEqualizerBandCount();
        JSONArray flat = new JSONArray();
        for (int i = 0; i < count; i++) flat.put(0);
        customBands = flat.toString();
        eqSelection = -2;
        applyCustomBands();
        prefs.edit().putInt("eq_preset", -2).putString("eq_custom_bands", customBands).apply();
        changed();
    }
    /** Named custom profiles live in app preferences and survive track changes. */
    public String[] namedEqualizerProfiles() {
        try {
            JSONObject profiles = new JSONObject(prefs.getString("eq_profiles", "{}"));
            JSONArray names = profiles.names();
            if (names == null) return new String[0];
            String[] result = new String[names.length()];
            for (int i = 0; i < names.length(); i++) result[i] = names.optString(i);
            java.util.Arrays.sort(result, String.CASE_INSENSITIVE_ORDER);
            return result;
        } catch (Exception ignored) { return new String[0]; }
    }
    public void saveEqualizerProfile(String name) {
        if (name == null || name.trim().isEmpty() || effect == null) return;
        try {
            if (eqSelection != -2) customBands = captureBandValues();
            eqSelection = -2;
            JSONObject profiles = new JSONObject(prefs.getString("eq_profiles", "{}"));
            profiles.put(name.trim(), new JSONArray(customBands));
            prefs.edit().putInt("eq_preset", -2).putString("eq_custom_bands", customBands)
                    .putString("eq_profiles", profiles.toString()).apply();
            applyCustomBands(); changed();
        } catch (Exception ignored) { }
    }
    public void loadEqualizerProfile(String name) {
        try {
            JSONObject profiles = new JSONObject(prefs.getString("eq_profiles", "{}"));
            JSONArray levels = profiles.optJSONArray(name);
            if (levels == null) return;
            customBands = levels.toString(); eqSelection = -2;
            prefs.edit().putInt("eq_preset", -2).putString("eq_custom_bands", customBands).apply();
            applyCustomBands(); changed();
        } catch (Exception ignored) { }
    }
    public void removeEqualizerProfile(String name) {
        try {
            JSONObject profiles = new JSONObject(prefs.getString("eq_profiles", "{}"));
            profiles.remove(name);
            prefs.edit().putString("eq_profiles", profiles.toString()).apply();
            changed();
        } catch (Exception ignored) { }
    }

    public boolean isBassBoosted() { return bassEnabled; }
    public void selectEqualizerPreset(int preset) {
        eqSelection = preset;
        prefs.edit().putInt("eq_preset", preset).apply();
        if (effect != null) {
            try {
                if (preset == -2) applyCustomBands();
                else if (preset < 0 || preset >= effect.getNumberOfPresets()) effect.setEnabled(false);
                else { effect.usePreset((short) preset); effect.setEnabled(true); }
            } catch (Exception ignored) {}
        }
        changed();
    }
    public void setBassBoosted(boolean enabled) {
        bassEnabled = enabled;
        prefs.edit().putBoolean("bass_boost", enabled).apply();
        if (bassBoost != null) {
            try {
                bassBoost.setStrength((short) 750);
                bassBoost.setEnabled(enabled);
            } catch (Exception ignored) {}
        }
        changed();
    }
    private void releaseAudioEffects() {
        if (effect != null) { try { effect.release(); } catch (Exception ignored) {} effect = null; }
        if (bassBoost != null) { try { bassBoost.release(); } catch (Exception ignored) {} bassBoost = null; }
    }
    private void attachAudioEffects() {
        releaseAudioEffects();
        if (player == null) return;
        int sessionId = player.getAudioSessionId();
        try {
            effect = new Equalizer(0, sessionId);
            if (eqSelection == -2) applyCustomBands();
            else if (eqSelection >= 0 && eqSelection < effect.getNumberOfPresets()) {
                effect.usePreset((short) eqSelection);
                effect.setEnabled(true);
            } else effect.setEnabled(false);
        } catch (Exception e) { effect = null; }
        try {
            bassBoost = new BassBoost(0, sessionId);
            bassBoost.setStrength((short) 750);
            bassBoost.setEnabled(bassEnabled);
        } catch (Exception e) { bassBoost = null; }
    }


    private final Runnable sleepAction = () -> {
        if (sleepDeadlineElapsed > 0 && android.os.SystemClock.elapsedRealtime() >= sleepDeadlineElapsed) {
            sleepDeadlineElapsed = 0;
            PlaybackDiagnostics.event(this, "MINUTEUR", "Minuteur de sommeil terminé");
            pause();
        }
    };
    public long getSleepMillisRemaining() {
        return sleepDeadlineElapsed == 0 ? 0L : Math.max(0L,
                sleepDeadlineElapsed - android.os.SystemClock.elapsedRealtime());
    }
    public int getSleepMinutesRemaining() {
        if (sleepDeadlineElapsed == 0) return 0;
        return (int) Math.max(1, (sleepDeadlineElapsed - android.os.SystemClock.elapsedRealtime() + 59999) / 60000);
    }
    public void setSleepMinutes(int minutes) {
        main.removeCallbacks(sleepAction);
        if (minutes <= 0) sleepDeadlineElapsed = 0;
        else {
            sleepDeadlineElapsed = android.os.SystemClock.elapsedRealtime() + minutes * 60000L;
            main.postDelayed(sleepAction, minutes * 60000L);
        }
        changed();
    }


    public void playTracks(List<Track> tracks, int selected) {
        if (tracks == null || tracks.isEmpty() || selected < 0 || selected >= tracks.size()) return;
        ensureStartedForPlayback();
        order.clear(); original.clear(); pending.clear(); history.clear();
        for (Track t : tracks) { order.add(t.id); original.add(t.id); }
        index = selected;
        if (shuffle) {
            long chosen = order.remove(selected);
            Collections.shuffle(order, random);
            order.add(0, chosen);
            index = 0;
        }
        consecutiveErrors = 0;
        open(MusicRepository.get(order.get(index)), true, 0);
        save();
    }

    public void toggle() { if (playing) pause(); else resume(); }

    public void resume() {
        if (current == null) return;
        ensureStartedForPlayback();
        if (!prepared || player == null) {
            startOnReady = true;
            if (!loading) open(current, true, resumePosition);
            return;
        }
        if (playing) return;
        if (!requestFocus()) {
            PlaybackDiagnostics.event(this, "PRIORITE_AUDIO_REFUSEE", "Autre application prioritaire");
            return;
        }
        try {
            player.start();
            playing = true;
            listeningStartedElapsed = SystemClock.elapsedRealtime();
            PlaybackDiagnostics.event(this, "LECTURE", "Titre lancé ou repris");
            if (!playLogged) {
                playLogged = true;
                try { listeningStore.recordPlay(current.id); } catch (Exception ignored) {}
            }
            resumeAfterFocus = false;
            syncSession(); updateNotification(); changed();
        } catch (Exception e) { recoverError(); }
    }

    public void pause() { pauseInternal(true); }
    private void pauseInternal(boolean releaseFocus) {
        releasePreparationWake();
        flushListening();
        listeningStartedElapsed = 0L;
        startOnReady = false;
        if (player != null && prepared) {
            try { if (player.isPlaying()) player.pause(); } catch (Exception ignored) {}
        }
        playing = false;
        if (releaseFocus) resumeAfterFocus = false;
        resumePosition = getPosition();
        if (releaseFocus) abandonFocus();
        syncSession(); updateNotification(); save(); changed();
    }

    private void abandonFocus() {
        if (audio == null) return;
        if (focusRequest != null) audio.abandonAudioFocusRequest(focusRequest);
    }

    public void seek(long ms) {
        if (current == null) return;
        long duration = getDuration();
        long clamped = Math.max(0L, Math.min(ms, duration > 0 ? duration : ms));
        resumePosition = clamped;
        if (player != null && prepared) {
            try { player.seekTo((int) Math.min(Integer.MAX_VALUE, clamped)); } catch (Exception ignored) {}
        }
        syncSession(); save(); changed();
    }

    public void next() { advance(false); }
    private void advance(boolean endedNaturally) {
        if (current == null) return;
        flushListening();
        listeningStartedElapsed = 0L;
        if (endedNaturally && repeat == 2) {
            playing = false;
            playLogged = false; // A completed repeat is a new listen.
            seek(0);
            resume();
            return;
        }
        history.add(current.id);
        if (!pending.isEmpty()) {
            Long nextId = pending.remove(0);
            open(MusicRepository.get(nextId), true, 0);
        } else if (index + 1 < order.size()) {
            index++;
            open(MusicRepository.get(order.get(index)), true, 0);
        } else if (repeat == 1 && !order.isEmpty()) {
            index = 0;
            if (shuffle) Collections.shuffle(order, random);
            open(MusicRepository.get(order.get(index)), true, 0);
        } else {
            playing = false;
            startOnReady = false;
            PlaybackDiagnostics.event(this, "FIN_FILE", "Dernier titre atteint (répétition désactivée)");
            if (player != null && prepared) {
                try { player.pause(); player.seekTo(0); } catch (Exception ignored) {}
            }
            resumePosition = 0;
            abandonFocus();
            syncSession(); updateNotification(); save(); changed();
            return;
        }
        save();
    }

    public void previous() {
        if (current == null) return;
        if (getPosition() > 3000L) { seek(0); return; }
        if (!history.isEmpty()) {
            long id = history.remove(history.size() - 1);
            // If the earlier song belongs to the base order, go back in that order.
            int base = order.indexOf(id);
            if (base >= 0) index = base;
            else pending.add(0, current.id);
            open(MusicRepository.get(id), true, 0);
        } else if (index > 0) {
            index--;
            open(MusicRepository.get(order.get(index)), true, 0);
        } else seek(0);
        save();
    }

    public void playNext(Track track) {
        if (track == null) return;
        if (current == null) {
            playTracks(Collections.singletonList(track), 0);
            return;
        }
        pending.add(0, track.id);
        save(); changed();
    }
    public void enqueue(Track track) {
        if (track == null) return;
        if (current == null) {
            playTracks(Collections.singletonList(track), 0);
            return;
        }
        pending.add(track.id);
        save(); changed();
    }

    /** Reorder all upcoming items, including automatically queued album/library tracks. */
    public void reorder(int from, int to) {
        List<Track> items = upcoming();
        if (from < 0 || to < 0 || from >= items.size() || to >= items.size() || current == null) return;
        Track moved = items.remove(from);
        items.add(to, moved);
        replaceUpcoming(items);
    }
    /** Remove stale queue/history references after the system deletes a media file. */
    public void forgetDeletedTrack(long deletedId) {
        pending.removeIf(id -> id == deletedId);
        history.removeIf(id -> id == deletedId);
        original.removeIf(id -> id == deletedId);
        for (int i = order.size() - 1; i >= 0; i--) {
            if (order.get(i) == deletedId) {
                order.remove(i);
                if (i < index) index--;
            }
        }
        save();
        changed();
    }

    public void removeUpcoming(int at) {
        List<Track> items = upcoming();
        if (at < 0 || at >= items.size() || current == null) return;
        items.remove(at);
        replaceUpcoming(items);
    }
    private void replaceUpcoming(List<Track> items) {
        order.clear(); order.add(current.id);
        for (Track t : items) order.add(t.id);
        original.clear(); original.addAll(order);
        pending.clear(); index = 0;
        save(); changed();
    }

    public void setShuffle(boolean enabled) {
        if (shuffle == enabled) return;
        shuffle = enabled;
        if (enabled) shuffleTail();
        else restoreTail();
        save(); changed();
    }
    private void shuffleTail() {
        if (index < 0 || index + 1 >= order.size()) return;
        Collections.shuffle(order.subList(index + 1, order.size()), random);
    }
    private void restoreTail() {
        if (index < 0 || index + 1 >= order.size()) return;
        Map<Long, Integer> position = new HashMap<>();
        for (int i = 0; i < original.size(); i++) position.put(original.get(i), i);
        order.subList(index + 1, order.size()).sort((a, b) -> Integer.compare(
                position.getOrDefault(a, Integer.MAX_VALUE), position.getOrDefault(b, Integer.MAX_VALUE)));
    }
    public void cycleRepeat() { repeat = (repeat + 1) % 3; save(); changed(); }

    public void restore() {
        if (current != null || !MusicRepository.isLoaded()) return;
        try {
            order.clear(); original.clear(); pending.clear(); history.clear();
            readIds(prefs.getString("order", "[]"), order);
            readIds(prefs.getString("original", "[]"), original);
            readIds(prefs.getString("pending", "[]"), pending);
            readIds(prefs.getString("history", "[]"), history);
            index = prefs.getInt("index", -1);
            long currentId = prefs.getLong("current", -1L);
            Track track = MusicRepository.get(currentId);
            if (track != null) {
                if (index < 0 || index >= order.size() || order.get(index) != currentId) {
                    index = order.indexOf(currentId);
                    if (index < 0) { order.clear(); order.add(currentId); original.clear(); original.add(currentId); index = 0; }
                }
                resumePosition = prefs.getLong("position", 0L);
                open(track, false, resumePosition);
            }
        } catch (Exception ignored) { order.clear(); original.clear(); pending.clear(); index = -1; }
    }
    private void readIds(String json, List<Long> dest) throws Exception {
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            long id = array.getLong(i);
            if (MusicRepository.get(id) != null) dest.add(id);
        }
    }
    private void save() {
        if (prefs == null) return;
        prefs.edit()
                .putString("order", new JSONArray(order).toString())
                .putString("original", new JSONArray(original).toString())
                .putString("pending", new JSONArray(pending).toString())
                .putString("history", new JSONArray(history).toString())
                .putInt("index", index)
                .putInt("repeat", repeat)
                .putBoolean("shuffle", shuffle)
                .putLong("current", current == null ? -1L : current.id)
                .putString("current_title", current == null ? "Votre musique, partout" : current.title)
                .putString("current_artist", current == null ? "Onde Player" : current.artist)
                .putLong("position", getPosition())
                .putBoolean("wants_playback", PlaybackContinuityRules.wantsPlayback(
                        playing, loading, startOnReady, resumeAfterFocus, restoringPlayback))
                .apply();
    }

    private void open(Track track, boolean autoStart, long position) {
        if (autoStart) ensureStartedForPlayback();
        flushListening();
        listeningStartedElapsed = 0L;
        if (track == null) { recoverError(); return; }
        releaseAudioEffects();
        if (player != null) { try { player.reset(); player.release(); } catch (Exception ignored) {} }
        current = track;
        videoWidth = 0; videoHeight = 0; renderedVideoFrame = false;
        currentArtwork = null;
        Artwork.request(this, track, bitmap -> {
            if (current != null && current.id == track.id) {
                currentArtwork = bitmap;
                syncSession();
                updateNotification();
            }
        });
        playLogged = false;
        player = new MediaPlayer();
        player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        player.setOnInfoListener((mp, what, extra) -> {
            if (player == mp && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                renderedVideoFrame = true;
                changed();
            }
            return false;
        });
        player.setOnVideoSizeChangedListener((mp, width, height) -> {
            if (player == mp) { videoWidth = width; videoHeight = height; changed(); }
        });
        prepared = false;
        loading = true;
        playing = false;
        startOnReady = autoStart;
        resumePosition = Math.max(0, position);
        if (autoStart && preparationWakeLock != null) {
            try { preparationWakeLock.acquire(90000L); } catch (RuntimeException ignored) {}
        }
        if (autoStart) PlaybackDiagnostics.event(this, "CHARGEMENT_TITRE", "Préparation audio, service maintenu");
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(track.isVideo ? AudioAttributes.CONTENT_TYPE_MOVIE : AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA).build());
        applyVideoSurface();
        final MediaPlayer instance = player;
        player.setOnPreparedListener(mp -> {
            if (player != instance) return;
            releasePreparationWake();
            prepared = true;
            if (track.isVideo) {
                try { videoWidth = mp.getVideoWidth(); videoHeight = mp.getVideoHeight(); }
                catch (RuntimeException ignored) { }
            }
            loading = false;
            consecutiveErrors = 0;
            attachAudioEffects();
            applyVideoSurface();
            if (playbackSpeed != 1f) {
                try {
                    mp.setPlaybackParams(new PlaybackParams().setSpeed(playbackSpeed));
                    // setPlaybackParams can start a prepared paused MediaPlayer.
                    if (!startOnReady) mp.pause();
                } catch (Exception ignored) { }
            }
            if (resumePosition > 0) {
                try { mp.seekTo((int) Math.min(Integer.MAX_VALUE, resumePosition)); } catch (Exception ignored) {}
            }
            if (startOnReady) resume();
            else { syncSession(); changed(); }
        });
        player.setOnCompletionListener(mp -> { if (player == instance) advance(true); });
        player.setOnErrorListener((mp, what, extra) -> {
            if (player == instance) {
                PlaybackDiagnostics.event(this, "ERREUR_DECODAGE", what + "/" + extra);
                reportVideoError(what, extra);
                recoverError();
            }
            return true;
        });
        try {
            player.setDataSource(this, track.uri);
            player.prepareAsync();
            syncSession();
            updateNotification();
            changed();
        } catch (Exception e) {
            PlaybackDiagnostics.event(this, "ERREUR_OUVERTURE", e.getClass().getSimpleName());
            if (track.isVideo) reportVideoError(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            recoverError();
        }
    }
    private void releasePreparationWake() {
        if (preparationWakeLock != null && preparationWakeLock.isHeld()) {
            try { preparationWakeLock.release(); } catch (RuntimeException ignored) {}
        }
    }
    private void recoverError() {
        releasePreparationWake();
        consecutiveErrors++;
        if (consecutiveErrors > Math.max(3, order.size() + pending.size() + 1)) {
            flushListening(); listeningStartedElapsed = 0L;
            loading = false; playing = false; startOnReady = false;
            PlaybackDiagnostics.event(this, "ERREURS_CONSECUTIVES", "Lecture interrompue après erreurs répétées");
            syncSession(); updateNotification(); save(); changed();
            return;
        }
        main.post(() -> advance(false));
    }
    private boolean requestFocus() {
        if (Build.VERSION.SDK_INT >= 26) {
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setOnAudioFocusChangeListener(this::audioFocusChanged).build();
            }
            return audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
        return audio.requestAudioFocus(this::audioFocusChanged, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }
    private void audioFocusChanged(int change) {
        main.post(() -> {
            if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                // Keep the foreground service alive while GPS/phone calls take
                // temporary focus. The previous implementation revoked foreground
                // status BEFORE remembering that playback should resume.
                boolean shouldResume = playing || (loading && startOnReady);
                resumeAfterFocus = shouldResume;
                PlaybackDiagnostics.event(this, "PRIORITE_AUDIO_TEMPORAIRE", "Pause en attente du retour audio");
                pauseInternal(false);
            } else if (change == AudioManager.AUDIOFOCUS_LOSS) {
                PlaybackDiagnostics.event(this, "PRIORITE_AUDIO_PERDUE", "Autre application audio prioritaire");
                resumeAfterFocus = false;
                pause();
            } else if (change == AudioManager.AUDIOFOCUS_GAIN && resumeAfterFocus) {
                PlaybackDiagnostics.event(this, "PRIORITE_AUDIO_REVENUE", "Reprise automatique");
                resumeAfterFocus = false;
                resume();
            } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK && player != null) {
                PlaybackDiagnostics.event(this, "VOLUME_REDUIT", "GPS ou autre application audio");
                try { player.setVolume(0.25f, 0.25f); } catch (Exception ignored) {}
            } else if (change == AudioManager.AUDIOFOCUS_GAIN && player != null) {
                try { player.setVolume(1f, 1f); } catch (Exception ignored) {}
            }
        });
    }
    public void stopPlayback() {
        PlaybackDiagnostics.event(this, "ARRET_MANUEL", "Action Arrêter");
        hasStartedLifetime = false;
        releasePreparationWake();
        flushListening(); listeningStartedElapsed = 0L;
        playing = false; loading = false; prepared = false; startOnReady = false;
        if (player != null) { try { player.reset(); player.release(); } catch (Exception ignored) {} player = null; }
        releaseAudioEffects();
        currentArtwork = null;
        abandonFocus();
        current = null; order.clear(); original.clear(); pending.clear(); history.clear(); index = -1;
        resumePosition = 0;
        save(); syncSession();
        if (isForeground) { stopForeground(true); isForeground = false; }
        notifications.cancel(NOTIFICATION_ID);
        hasPublishedNotification = false;
        changed(); stopSelf();
    }

    private void syncSession() {
        if (session == null) return;
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE |
                PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT |
                PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP;
        int state = current == null ? PlaybackState.STATE_STOPPED :
                loading ? PlaybackState.STATE_BUFFERING :
                playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions).setState(state, getPosition(), playing ? playbackSpeed : 0f).build());
        if (current != null) {
            MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, current.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, current.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, current.album)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, current.durationMs);
            if (currentArtwork != null) metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, currentArtwork);
            session.setMetadata(metadata.build());
        }
    }

    private PendingIntent actionIntent(String action, int id) {
        Intent intent = new Intent(this, PlaybackService.class).setAction(action);
        return PendingIntent.getService(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 20, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(current == null ? "Onde" : current.title)
                .setContentText(current == null ? "Lecteur musical" : current.artist)
                .setLargeIcon(currentArtwork)
                .setContentIntent(content)
                .setColor(Ui.ACCENT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(playing)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous, "Précédent", actionIntent(ACTION_PREVIOUS, 1)).build())
                .addAction(new Notification.Action.Builder(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Pause" : "Lecture", actionIntent(ACTION_PLAY_PAUSE, 2)).build())
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next, "Suivant", actionIntent(ACTION_NEXT, 3)).build())
                .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2))
                .build();
    }
    private void updateNotification() {
        if (current == null) return;
        Notification n = notification();
        boolean keep = PlaybackContinuityRules.keepForeground(
                playing, loading, startOnReady, resumeAfterFocus, restoringPlayback);
        try {
            if (keep) {
                // In particular, do NOT demote the service while a new track is
                // preparing or the car's audio focus is temporarily lost.
                startForeground(NOTIFICATION_ID, n);
                isForeground = true;
                hasPublishedNotification = true;
            } else if (isForeground) {
                stopForeground(false);
                isForeground = false;
                notifications.notify(NOTIFICATION_ID, n);
                hasPublishedNotification = true;
            } else if (hasPublishedNotification) notifications.notify(NOTIFICATION_ID, n);
        } catch (RuntimeException e) {
            PlaybackDiagnostics.event(this, "SERVICE_NON_PROTEGE", e.getClass().getSimpleName());
        }
    }

    @Override public void onDestroy() {
        PlaybackDiagnostics.event(this, "SERVICE_TERMINE", "Actif=" + playing
                + ", chargement=" + loading + ", autonome=" + hasStartedLifetime
                + ", premierPlan=" + isForeground);
        releasePreparationWake();
        flushListening(); listeningStartedElapsed = 0L;
        save();
        if (becomingNoisy != null) try { unregisterReceiver(becomingNoisy); } catch (Exception ignored) {}
        releaseAudioEffects();
        if (player != null) try { player.release(); } catch (Exception ignored) {}
        if (focusRequest != null) audio.abandonAudioFocusRequest(focusRequest);
        if (session != null) { session.setActive(false); session.release(); }
        if (listeningStore != null) listeningStore.close();
        main.removeCallbacks(sleepAction);
        main.removeCallbacks(checkpoint);
        if (isForeground) stopForeground(true);
        notifications.cancel(NOTIFICATION_ID);
        super.onDestroy();
    }
}
