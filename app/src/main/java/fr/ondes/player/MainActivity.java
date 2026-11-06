package fr.ondes.player;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.graphics.drawable.ColorDrawable;
import android.widget.CompoundButton;
import android.widget.ListAdapter;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.database.ContentObserver;
import android.provider.MediaStore;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.DragEvent;
import android.content.SharedPreferences;
import android.view.View;
import android.view.TextureView;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.MotionEvent;
import android.os.SystemClock;
import android.view.WindowManager;
import android.content.pm.ActivityInfo;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Main application UI, built with platform widgets to be compilable in AndroidIDE. */
public final class MainActivity extends Activity {
    private static final int READ_REQUEST = 501;
    private static final int VIDEO_REQUEST = 503;
    private static final int NOTIFICATION_REQUEST = 502;
    private static final int IMPORT_PLAYLIST = 710;
    private static final int EXPORT_PLAYLIST = 711;
    private static final int DELETE_MEDIA_REQUEST = 712;
    private static final int PICK_WALLPAPER_REQUEST = 713;
    private static final int LEGACY_WRITE_REQUEST = 504;
    private static final String[] SCREENS = {"Titres", "Albums", "Artistes", "Playlists", "Écoutés", "À suivre"};
    private static final long FAVORITES = -2L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Track> library = new ArrayList<>();
    private PlaybackService service;
    private boolean bound = false;
    private boolean hasScanned = false;
    private boolean lastVideoAccess = false;
    private boolean isScanning = false;
    private String screen = "Titres";
    private Long detailAlbum;
    private String detailArtist;
    private Long detailPlaylist;
    private String detailName;
    private LibraryStore store;
    private long exportPlaylistId;
    private String exportPlaylistName;

    private FrameLayout root;
    private ThemeEditorDialog themeEditor;
    private String pendingThemeImageTarget = "background";
    private FrameLayout content;
    private LinearLayout topTabs;
    private View tabHeader;
    private LinearLayout mini;
    private FrameLayout fullOverlay;
    private EditText search;
    private TextView headerSubtitle;
    private View brandHeader;
    private View brandTitleStack, pageHeading, pageTypes, pageToolbar, pageHint;
    private TextView searchIcon;
    private boolean headerScrolled = false;
    private HeaderMotionController headerMotion;
    private Map<Long, Integer> playlistMembershipCounts = new HashMap<>();
    private Track pendingDelete;
    private ImageView miniCover, fullCover;
    private TextView miniTitle, miniArtist, miniPause, fullTitle, fullArtist, fullPause, shuffleButton, repeatButton, heartButton;
    private TextView elapsed, total, fullQueueCount;
    private SeekBar progress;
    private boolean userSeeking;
    private boolean topHistory = false;
    private ListView activeTrackList;
    private FrameLayout guideOverlay;
    private int sorting = 0;
    // 0 = all, 1 = audio only, 2 = video only.
    private int mediaFilter = 0;
    private FrameLayout artworkFrame;
    private ScrollView fullScroll;
    private FrameLayout videoPane;
    private TextureView videoSurface;
    private Surface attachedVideoSurface;
    private boolean textureFrameReady = false;
    private TextView videoMessage;
    private long observedVideoId = Long.MIN_VALUE;
    private long videoObservedElapsed = 0L;
    private int lastShownVideoErrorSerial = 0;
    private FrameLayout sleepBubble;
    private TextView sleepBubbleText;
    private FrameLayout fullscreenControls;
    private TextView videoFullButton;
    private TextView videoAudioButton;
    private TextView videoSpeedButton;
    private TextView fullscreenPause;
    private TextView fullscreenElapsed;
    private TextView fullscreenTotal;
    private SeekBar fullscreenSeek;
    private boolean isVideoFullscreen = false;
    private boolean fullscreenUserSeeking = false;
    private boolean videoControlsVisible = true;
    private long lastPresentedVideoId = Long.MIN_VALUE;
    private FrameLayout miniCard;
    private View miniProgress;
    private long artworkTrack = -1;
    private final Runnable rescan = () -> { if (hasAudioPermission() && !isScanning) scanLibrary(); };
    private final ContentObserver mediaObserver = new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) {
            handler.removeCallbacks(rescan);
            handler.postDelayed(rescan, 1700);
        }
    };

    private final Runnable playerListener = this::refreshPlayer;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateProgress();
            updateSleepBubble();
            updateVideoMessage();
            handler.postDelayed(this, 500);
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((PlaybackService.LocalBinder) binder).service();
            service.listen(playerListener);
            attachVideoTextureIfReady();
            if (hasScanned) service.restore();
            refreshPlayer();
            updateSleepBubble();
            if (hasScanned) render();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            if (service != null) service.unlisten(playerListener);
            service = null;
            refreshPlayer();
            updateSleepBubble();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            exportPlaylistId = savedInstanceState.getLong("onde_export_playlist_id", 0L);
            exportPlaylistName = savedInstanceState.getString("onde_export_playlist_name");
            pendingThemeImageTarget = savedInstanceState.getString("onde_image_target", "background");
        }
        Ui.configure(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        store = new LibraryStore(this);
        lastVideoAccess = hasVideoPermission();
        sorting = getSharedPreferences("onde_settings", MODE_PRIVATE).getInt("track_sort", 0);
        mediaFilter = getSharedPreferences("onde_settings", MODE_PRIVATE).getInt("media_filter", 0);
        buildInterface();
        if (hasAudioPermission() || hasVideoPermission()) scanLibrary();
        else showPermissionScreen();
        // Local music scanning takes priority over an optional network update request.
        checkForUpdates(false);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putLong("onde_export_playlist_id", exportPlaylistId);
        outState.putString("onde_export_playlist_name", exportPlaylistName);
        outState.putString("onde_image_target", pendingThemeImageTarget);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onResume() {
        super.onResume();
        boolean videoAccess = hasVideoPermission();
        if (videoAccess != lastVideoAccess) {
            lastVideoAccess = videoAccess;
            if (hasAudioPermission() || videoAccess) scanLibrary();
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (!bound) {
            bound = bindService(new Intent(this, PlaybackService.class), connection, BIND_AUTO_CREATE);
        }
        getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);
        getContentResolver().registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);
        handler.post(ticker);
    }
    @Override protected void onStop() {
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(rescan);
        getContentResolver().unregisterContentObserver(mediaObserver);
        if (bound) {
            if (service != null) service.setVideoSurface(null);
            if (service != null) service.unlisten(playerListener);
            unbindService(connection);
            bound = false;
            service = null;
        }
        super.onStop();
    }
    @Override protected void onDestroy() {
        if (themeEditor != null) themeEditor.dismiss();
        if (isVideoFullscreen) leaveVideoFullscreen();
        io.shutdownNow();
        store.close();
        super.onDestroy();
    }

    private boolean hasAudioPermission() { return MusicRepository.hasAudioAccess(this); }
    private boolean hasVideoPermission() { return MusicRepository.hasVideoAccess(this); }
    private void requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= 34) requestPermissions(new String[]{
                Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED}, READ_REQUEST);
        else if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{
                Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO}, READ_REQUEST);
        else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_REQUEST);
    }
    private void requestVideoPermission() {
        if (Build.VERSION.SDK_INT >= 34)
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED}, VIDEO_REQUEST);
        else if (Build.VERSION.SDK_INT >= 33 && !hasVideoPermission())
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO}, VIDEO_REQUEST);
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LEGACY_WRITE_REQUEST) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED && pendingDelete != null)
                requestSystemMediaDeletion();
            else { pendingDelete = null; toast("Autorisation refusée : aucun fichier supprimé"); }
            return;
        }
        if (requestCode == READ_REQUEST || requestCode == VIDEO_REQUEST) {
            lastVideoAccess = hasVideoPermission();
            if (hasAudioPermission() || hasVideoPermission()) scanLibrary();
            else showPermissionScreen();
        }
    }
    private void maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED && !getPreferences(MODE_PRIVATE).getBoolean("asked_notification", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("asked_notification", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
        }
    }

    private void scanLibrary() {
        if (isScanning) return;
        isScanning = true;
        showLoading();
        io.execute(() -> {
            List<Track> discovered;
            Exception failure = null;
            try { discovered = MusicRepository.scan(getApplicationContext()); }
            catch (Exception e) { discovered = new ArrayList<>(); failure = e; }
            final List<Track> tracks = discovered;
            final Exception error = failure;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                isScanning = false;
                hasScanned = error == null;
                library.clear(); library.addAll(tracks);
                long videos = tracks.stream().filter(t -> t.isVideo).count();
                headerSubtitle.setText((tracks.size() - videos) + " musiques  ·  " + videos + " vidéos");
                if (error != null) {
                    content.removeAllViews();
                    showEmpty(content, "Impossible de lire la bibliothèque", "Vérifie l'autorisation d'accès à la musique.", "Réessayer", this::requestAudioPermission);
                } else {
                    if (service != null) service.restore();
                    render();
                    if (!tracks.isEmpty()) {
                        maybeRequestNotifications();
                        root.postDelayed(this::maybeShowFirstRunGuide, 450);
                    }
                    if (Build.VERSION.SDK_INT >= 33 && hasAudioPermission() && !hasVideoPermission() &&
                            !getSharedPreferences("onde_settings", MODE_PRIVATE).getBoolean("video_permission_asked", false)) {
                        getSharedPreferences("onde_settings", MODE_PRIVATE).edit().putBoolean("video_permission_asked", true).apply();
                        root.postDelayed(this::requestVideoPermission, 1100);
                    }
                }
            });
        });
    }

    private void buildInterface() {
        root = new FrameLayout(this);
        ThemeGradient.apply(this, root);
        setContentView(root);
        ThemeBackground.attach(this, root);
        LinearLayout shell = Ui.vertical(this);
        FrameLayout.LayoutParams shellParams = new FrameLayout.LayoutParams(-1, -1);
        root.addView(shell, shellParams);

        LinearLayout header = Ui.horizontal(this);
        header.setPadding(d(17), d(7), d(17), d(5));
        LinearLayout titleStack = Ui.vertical(this);
        TextView logo = Ui.text(this, "onde.", 27, Ui.TEXT, true);
        titleStack.addView(logo);
        brandTitleStack = titleStack;
        header.addView(titleStack, new LinearLayout.LayoutParams(0, -2, 1f));
        searchIcon = Ui.icon(this, "⌕", 42, 25, Ui.ACCENT, Ui.SURFACE);
        searchIcon.setContentDescription("Rechercher un titre, un artiste ou une vidéo");
        searchIcon.setOnClickListener(v -> openSearchDialog());
        LinearLayout.LayoutParams searchP = Ui.lp(this, 42, 42);
        searchP.rightMargin = d(9);
        header.addView(searchIcon, searchP);
        TextView refresh = Ui.icon(this, "↻", 42, 25, Ui.TEXT, Ui.SURFACE);
        refresh.setContentDescription("Actualiser la bibliothèque");
        refresh.setOnClickListener(v -> { if (hasAudioPermission()) scanLibrary(); else requestAudioPermission(); });
        LinearLayout.LayoutParams refreshP = Ui.lp(this, 42, 42);
        refreshP.rightMargin = d(9);
        header.addView(refresh, refreshP);
        TextView libraryOptions = Ui.icon(this, "⋮", 44, 26, Ui.TEXT, Ui.SURFACE);
        libraryOptions.setContentDescription("Options de la bibliothèque");
        LinearLayout.LayoutParams menuP = Ui.lp(this, 44, 44);
        menuP.leftMargin = d(0);
        header.addView(libraryOptions, menuP);
        libraryOptions.setOnClickListener(v -> showLibraryOptions());
        shell.addView(header);
        brandHeader = header;

        // Search remains a state holder but no longer consumes permanent screen height.
        headerSubtitle = Ui.text(this, "", 11, Ui.MUTED, false);
        headerSubtitle.setVisibility(View.GONE);
        search = new EditText(this);
        search.setVisibility(View.GONE);
        shell.addView(search, new LinearLayout.LayoutParams(0, 0));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchIcon != null) searchIcon.setTextColor(s.length() == 0 ? Ui.ACCENT : Ui.PURPLE);
                if (hasScanned) render();
            }
            @Override public void afterTextChanged(Editable e) {}
        });

        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        topTabs = Ui.horizontal(this);
        topTabs.setPadding(d(20), 0, d(20), d(12));
        horizontal.addView(topTabs);
        shell.addView(horizontal);
        tabHeader = horizontal;
        rebuildTabs();

        content = new FrameLayout(this);
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        mini = Ui.horizontal(this);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setPadding(d(10), d(8), d(8), d(8));
        Ui.clickable(mini, Ui.gradient(0xFF2D352D, 0xFF222936, 17, this));
        miniCover = cover(47, 11);
        mini.addView(miniCover, Ui.lp(this, 47, 47));
        LinearLayout miniText = Ui.vertical(this);
        miniText.setPadding(d(12), 0, d(4), 0);
        miniTitle = Ui.text(this, "", 14, Ui.TEXT, true);
        miniTitle.setSingleLine(true);
        miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        miniArtist = Ui.text(this, "", 11, Ui.MUTED, false);
        miniArtist.setSingleLine(true);
        miniArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        miniText.addView(miniTitle);
        miniText.addView(Ui.gap(this, 4));
        miniText.addView(miniArtist);
        mini.addView(miniText, new LinearLayout.LayoutParams(0, -2, 1f));
        miniPause = Ui.icon(this, "▶", 38, 20, Ui.BG, Ui.ACCENT);
        miniPause.setOnClickListener(v -> { if (service != null) service.toggle(); });
        LinearLayout.LayoutParams miniPlayP = Ui.lp(this, 38, 38);
        miniPlayP.rightMargin = d(8);
        mini.addView(miniPause, miniPlayP);
        TextView next = Ui.icon(this, "⏭", 38, 23, Ui.TEXT, Color.TRANSPARENT);
        next.setContentDescription("Morceau suivant");
        next.setOnClickListener(v -> { if (service != null) service.next(); });
        mini.addView(next);
        mini.setOnClickListener(v -> showFullPlayer());
        LinearLayout.LayoutParams miniParams = Ui.lp(this, -1, 63);
        miniParams.setMargins(d(12), d(3), d(12), d(5));
        miniCard = new FrameLayout(this);
        miniCard.addView(mini, new FrameLayout.LayoutParams(-1, -1));
        View rail = new View(this);
        rail.setBackgroundColor(0xFF394A3C);
        miniCard.addView(rail, new FrameLayout.LayoutParams(-1, d(3), Gravity.BOTTOM));
        miniProgress = new View(this);
        miniProgress.setBackground(Ui.shape(Ui.ACCENT, 3, this));
        miniCard.addView(miniProgress, new FrameLayout.LayoutParams(0, d(3), Gravity.BOTTOM));
        shell.addView(miniCard, miniParams);
        miniCard.setVisibility(View.GONE);

        fullOverlay = new FrameLayout(this);
        fullOverlay.setBackgroundColor(Ui.BG);
        fullOverlay.setVisibility(View.GONE);
        root.addView(fullOverlay, new FrameLayout.LayoutParams(-1, -1));
        ThemeBackground.attach(this, fullOverlay);
        buildFullPlayer();
        buildSleepBubble();
    }

    /** In-app floating sleep timer, above both the library and fullscreen video. */
    private void buildSleepBubble() {
        sleepBubble = new FrameLayout(this);
        sleepBubble.setBackground(Ui.gradient(0xF72D4035, 0xF020302A, 22, this));
        sleepBubble.setElevation(d(15));
        sleepBubble.setPadding(d(13), d(8), d(13), d(8));
        sleepBubbleText = Ui.text(this, "◷  00:00", 14, Ui.TEXT, true);
        sleepBubbleText.setGravity(Gravity.CENTER);
        sleepBubbleText.setContentDescription("Minuteur de sommeil, toucher pour modifier, glisser pour déplacer");
        sleepBubble.addView(sleepBubbleText, new FrameLayout.LayoutParams(-2, -1));
        FrameLayout.LayoutParams bubbleLp = new FrameLayout.LayoutParams(-2, d(48), Gravity.TOP | Gravity.LEFT);
        root.addView(sleepBubble, bubbleLp);
        sleepBubble.setVisibility(View.GONE);
        sleepBubble.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if ((oldR - oldL) == 0 && (r - l) > 0) sleepBubble.post(this::positionSleepBubble);
        });
        root.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if ((r - l) != (oldR - oldL) || (b - t) != (oldB - oldT))
                sleepBubble.post(this::positionSleepBubble);
        });
        sleepBubble.post(this::positionSleepBubble);
        sleepBubble.setOnTouchListener(new View.OnTouchListener() {
            float lastX, lastY, downX, downY;
            boolean moved;
            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = downX = event.getRawX();
                        lastY = downY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - downX) > d(7) ||
                                Math.abs(event.getRawY() - downY) > d(7)) moved = true;
                        if (moved) {
                            float x = clampBubbleX(view.getX() + event.getRawX() - lastX);
                            float y = clampBubbleY(view.getY() + event.getRawY() - lastY);
                            view.setX(x);
                            view.setY(y);
                        }
                        lastX = event.getRawX();
                        lastY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            float xRange = Math.max(1, root.getWidth() - sleepBubble.getWidth() - d(12));
                            float yRange = Math.max(1, root.getHeight() - sleepBubble.getHeight() - d(12));
                            getSharedPreferences("onde_settings", MODE_PRIVATE).edit()
                                    .putFloat("bubble_x", (view.getX() - d(6)) / xRange)
                                    .putFloat("bubble_y", (view.getY() - d(6)) / yRange).apply();
                        } else showSleepDialog();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        positionSleepBubble();
                        return true;
                }
                return true;
            }
        });
    }

    private float clampBubbleX(float x) {
        return Math.max(d(6), Math.min(x,
                Math.max(d(6), root.getWidth() - sleepBubble.getWidth() - d(6))));
    }
    private float clampBubbleY(float y) {
        return Math.max(d(6), Math.min(y,
                Math.max(d(6), root.getHeight() - sleepBubble.getHeight() - d(6))));
    }
    private void positionSleepBubble() {
        if (sleepBubble == null || sleepBubble.getWidth() == 0 || root.getWidth() == 0) return;
        float x = getSharedPreferences("onde_settings", MODE_PRIVATE).getFloat("bubble_x", 0.97f);
        float y = getSharedPreferences("onde_settings", MODE_PRIVATE).getFloat("bubble_y", 0.14f);
        sleepBubble.setX(clampBubbleX(d(6) + x * Math.max(1, root.getWidth() - sleepBubble.getWidth() - d(12))));
        sleepBubble.setY(clampBubbleY(d(6) + y * Math.max(1, root.getHeight() - sleepBubble.getHeight() - d(12))));
    }
    private void updateSleepBubble() {
        if (sleepBubble == null) return;
        long millis = service == null ? 0 : service.getSleepMillisRemaining();
        if (millis <= 0) {
            sleepBubble.setVisibility(View.GONE);
            return;
        }
        if (sleepBubble.getVisibility() != View.VISIBLE) {
            sleepBubble.setVisibility(View.VISIBLE);
            sleepBubble.post(this::positionSleepBubble);
        }
        long seconds = (millis + 999) / 1000;
        String formatted = seconds >= 3600
                ? String.format(Locale.FRANCE, "◷  %d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
                : String.format(Locale.FRANCE, "◷  %02d:%02d", seconds / 60, seconds % 60);
        if (!formatted.contentEquals(sleepBubbleText.getText())) sleepBubbleText.setText(formatted);
        sleepBubble.bringToFront();
    }

    private int d(float n) { return Ui.dp(this, n); }
    private ImageView cover(int size, int radius) {
        ImageView image = new ImageView(this);
        image.setBackground(Ui.gradient(0xFF344550, 0xFF353147, radius, this));
        image.setClipToOutline(true);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return image;
    }
    private TextView textButton(String value, int color) {
        TextView t = Ui.text(this, value, 13, color, true);
        t.setGravity(Gravity.CENTER);
        Ui.clickable(t, Ui.shape(Ui.SURFACE, 12, this));
        t.setPadding(d(14), d(10), d(14), d(10));
        return t;
    }
    /** Consistent vector-based chips, replacing Unicode symbols embedded in labels. */
    private LinearLayout actionChip(String glyph, String title, boolean accent) {
        LinearLayout chip = Ui.horizontal(this);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(d(10), d(8), d(12), d(8));
        Ui.clickable(chip, Ui.shape(accent ? 0xFF30483B : Ui.SURFACE, 12, this));
        TextView picture = Ui.icon(this, glyph, 24, 18, accent ? Ui.ACCENT : Ui.TEXT, Color.TRANSPARENT);
        picture.setClickable(false);
        chip.addView(picture, Ui.lp(this, 24, 24));
        TextView label = Ui.text(this, title, 12, accent ? Ui.ACCENT : Ui.TEXT, true);
        label.setPadding(d(5), 0, 0, 0);
        chip.addView(label);
        chip.setContentDescription(title);
        return chip;
    }

    private void rebuildTabs() {
        topTabs.removeAllViews();
        for (String name : SCREENS) {
            boolean selected = name.equals(screen);
            TextView chip = Ui.text(this, name, 13, selected ? Ui.BG : Ui.MUTED, selected);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(d(16), d(12), d(16), d(12));
            Ui.clickable(chip, Ui.shape(selected ? Ui.ACCENT : Ui.SURFACE, 22, this));
            LinearLayout.LayoutParams p = Ui.lp(this, -2, -2);
            p.rightMargin = d(7);
            topTabs.addView(chip, p);
            chip.setOnClickListener(v -> {
                screen = name;
                clearDetail();
                rebuildTabs();
                render();
            });
        }
    }
    private void clearDetail() {
        detailAlbum = null;
        detailArtist = null;
        detailPlaylist = null;
        detailName = null;
    }
    private void showLibraryOptions() {
        boolean allAudio = getSharedPreferences("onde_settings", MODE_PRIVATE)
                .getBoolean("include_all_audio", false);
        List<OptionSheet.Item> actions = OptionSheet.items();
        actions.add(new OptionSheet.Item("↻", "Actualiser les morceaux", "Rechercher les fichiers musicaux du téléphone", () -> {
            if (hasAudioPermission()) scanLibrary(); else requestAudioPermission();
        }));
        actions.add(new OptionSheet.Item("♫", allAudio ? "Musique uniquement" : "Tous les fichiers audio",
                allAudio ? "Masquer les enregistrements courts" : "Afficher aussi les enregistrements et fichiers courts", () -> {
            getSharedPreferences("onde_settings", MODE_PRIVATE).edit().putBoolean("include_all_audio", !allAudio).apply();
            if (hasAudioPermission()) scanLibrary();
        }));
        if (Build.VERSION.SDK_INT >= 33)
            actions.add(new OptionSheet.Item("▶", hasVideoPermission() ? "Gérer l'accès aux vidéos" : "Autoriser les vidéos",
                    "Donner accès à toutes les vidéos ou seulement à une sélection", this::requestVideoPermission));
        String[] mediaOptions = {"Tout afficher", "Musique uniquement", "Vidéos uniquement"};
        actions.add(new OptionSheet.Item("☷", "Filtre multimédia : " + mediaOptions[mediaFilter],
                "Choisir quels fichiers apparaissent dans les listes", this::showMediaFilterDialog));
        actions.add(new OptionSheet.Item("◷", "Mes statistiques", "Temps d'écoute, séries et défis", () -> {
            screen = "Écoutés"; clearDetail(); rebuildTabs(); render();
        }));
        actions.add(new OptionSheet.Item("⏱", "Minuteur de sommeil", "Arrêter la musique après un délai", this::showSleepDialog));
        actions.add(new OptionSheet.Item("≋", "Égaliseur personnalisé", "Bandes réglables, préréglages, profils sauvegardés", this::showEqualizerDialog));
        actions.add(new OptionSheet.Item("✎", "Personnaliser l'apparence", "Accent, fond, couleur du texte et image personnelle", this::showThemeMenu));
        actions.add(new OptionSheet.Item("↻", "Mises à jour", "Version installée, état et recherche des nouvelles versions", this::showUpdateSettings));
        actions.add(new OptionSheet.Item("ⓘ", "Revoir le guide", "Gestes de lecture, sélection et file d'attente", this::startGuide));
        actions.add(new OptionSheet.Item("ⓘ", "À propos", "Version 3.8.1 · Média local et privé", () ->
                new AlertDialog.Builder(this).setTitle("Onde Player 3.8.1")
                        .setMessage("Lecteur musical hors ligne. Aucun compte ni publicité.\n\nLes statistiques et les playlists restent sur cet appareil. Le temps d'écoute est mesuré à partir de la V3.")
                        .setPositiveButton("Fermer", null).show()));
        OptionSheet.show(this, "Votre bibliothèque", "Réglages et raccourcis", actions);
    }

    /** Optional, free, HTTPS update feed (for example a raw file on GitHub).
     * A release is only suggested, never downloaded or installed without an explicit tap. */
    private static final String DEFAULT_UPDATE_FEED = "https://raw.githubusercontent.com/angemort/onde-player/main/update.json";

    /** Un écran de vérification lisible, sans configuration technique exposée. */
    private void showUpdateSettings() {
        UpdatePanel.show(this, io, DEFAULT_UPDATE_FEED);
    }

    private void checkForUpdates(boolean manual) {
        SharedPreferences prefs = getSharedPreferences("onde_settings", MODE_PRIVATE);
        String url = DEFAULT_UPDATE_FEED;
        if (!UpdateChecker.https(url)) {
            if (manual) toast("Configure d'abord le lien de mise à jour");
            return;
        }
        long now = System.currentTimeMillis();
        if (!manual && now - prefs.getLong("updates_last_check_ms", 0L) < 24L * 60 * 60 * 1000) return;
        prefs.edit().putLong("updates_last_check_ms", now).apply();
        if (manual) toast("Recherche de mises à jour…");
        io.execute(() -> {
            try {
                UpdateChecker.Release release = UpdateChecker.fetch(url);
                int installed = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (release.versionCode <= installed) {
                        if (manual) toast("Onde Player est à jour");
                        return;
                    }
                    String notes = release.notes.length() > 1100
                            ? release.notes.substring(0, 1100) + "…" : release.notes;
                    new AlertDialog.Builder(this)
                            .setTitle("Nouvelle version " + release.versionName)
                            .setMessage(notes + "\n\nTéléchargement depuis la source configurée. Android demandera une confirmation pour installer l’APK. Conserve la même signature pour protéger tes données.")
                            .setNegativeButton("Plus tard", null)
                            .setPositiveButton("Télécharger", (dialog, which) -> {
                                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl))
                                        .addCategory(Intent.CATEGORY_BROWSABLE)); }
                                catch (Exception e) { toast("Impossible d'ouvrir le téléchargement"); }
                            }).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (manual && !isFinishing()) toast("Vérification impossible : " + e.getMessage());
                });
            }
        });
    }

    /** Theme changes rebuild the player while the simple three-part editor stays open. */
    private void showThemeMenu() {
        if (themeEditor != null) themeEditor.dismiss();
        themeEditor = new ThemeEditorDialog(this, new ThemeEditorDialog.Actions() {
            @Override public void onChanged() { applyThemeLive(); }
            @Override public void onChooseImage(String role) { pickThemeImage(role); }
            @Override public void onReset() {
                SharedPreferences.Editor e = getSharedPreferences("onde_settings", MODE_PRIVATE).edit();
                for (String target : new String[]{"accent", "background", "text"}) {
                    e.remove("theme_" + target).remove("theme_" + target + "_mode")
                            .remove("theme_" + target + "_start").remove("theme_" + target + "_end")
                            .remove("theme_" + target + "_angle").remove("theme_" + target + "_strength")
                            .remove("theme_" + target + "_image");
                }
                e.remove("theme_wallpaper").remove("theme_gradient_enabled")
                        .remove("theme_gradient_start").remove("theme_gradient_end")
                        .remove("theme_gradient_angle").remove("theme_gradient_strength").apply();
                applyThemeLive();
            }
        });
        themeEditor.show();
    }

    /** Rebuild only the UI; keep the bound audio service, queue, playback position and theme dialog. */
    private void applyThemeLive() {
        String query = search == null ? "" : search.getText().toString();
        int firstVisible = activeTrackList == null ? -1 : activeTrackList.getFirstVisiblePosition();
        int firstOffset = activeTrackList == null || activeTrackList.getChildCount() == 0 ? 0
                : activeTrackList.getChildAt(0).getTop();
        if (guideOverlay != null) dismissGuide(false);
        if (attachedVideoSurface != null && service != null) service.setVideoSurface(null);
        Ui.configure(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        buildInterface();
        if (search != null && !query.isEmpty()) search.setText(query);
        if (hasScanned) render();
        if (activeTrackList != null && firstVisible >= 0) {
            activeTrackList.setSelectionFromTop(firstVisible, firstOffset);
            activeTrackList.post(() -> {
                if (headerMotion != null) headerMotion.setImmediate(firstVisible > 0);
                else setScrolledHeader(firstVisible > 0);
            });
        }
        refreshPlayer();
        updateSleepBubble();
    }

    private void pickThemeImage(String role) {
        if (!"accent".equals(role) && !"background".equals(role) && !"text".equals(role)) return;
        pendingThemeImageTarget = role;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, PICK_WALLPAPER_REQUEST); }
        catch (Exception e) { toast("Aucun sélecteur d'images disponible"); }
    }

    private void maybeShowFirstRunGuide() {
        if (getSharedPreferences("onde_settings", MODE_PRIVATE).getBoolean("gestures_guide_v31_seen", false)) return;
        if (screen.equals("Titres") && activeTrackList != null && !library.isEmpty()) {
            showGuideStep(0);
        }
    }

    private void startGuide() {
        if (guideOverlay != null) dismissGuide(false);
        screen = "Titres";
        clearDetail();
        if (search != null) search.setText("");
        rebuildTabs(); render();
        root.postDelayed(() -> showGuideStep(0), 220);
    }

    private void dismissGuide(boolean remember) {
        if (guideOverlay != null) {
            root.removeView(guideOverlay);
            guideOverlay = null;
        }
        if (remember) getSharedPreferences("onde_settings", MODE_PRIVATE)
                .edit().putBoolean("gestures_guide_v31_seen", true).apply();
    }

    private void showGuideStep(int step) {
        if (isFinishing() || isDestroyed()) return;
        if (step >= 4) { dismissGuide(true); return; }
        if (activeTrackList == null || activeTrackList.getChildCount() == 0) {
            if (step == 0) root.postDelayed(() -> {
                if (activeTrackList != null && activeTrackList.getChildCount() > 0) showGuideStep(0);
            }, 300);
            return;
        }
        dismissGuide(false);
        guideOverlay = new FrameLayout(this);
        guideOverlay.setClickable(true);
        guideOverlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        root.addView(guideOverlay, new FrameLayout.LayoutParams(-1, -1));
        GuideScrimView scrim = new GuideScrimView(this);
        guideOverlay.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        scrim.setClickable(true);
        View highlight = step == 3 ? topTabs : activeTrackList.getChildAt(0);
        int[] rootXY = new int[2], elementXY = new int[2];
        root.getLocationOnScreen(rootXY);
        highlight.getLocationOnScreen(elementXY);
        scrim.highlight(elementXY[0] - rootXY[0], elementXY[1] - rootXY[1],
                elementXY[0] - rootXY[0] + highlight.getWidth(),
                elementXY[1] - rootXY[1] + highlight.getHeight());

        String[] titles = {"Touchez pour écouter", "Maintenez pour sélectionner",
                "Glissez pour organiser", "Tout retrouver en un geste"};
        String[] messages = {
                "Un toucher sur un titre lance immédiatement sa lecture. Le bouton ⋮ ouvre ses options.",
                "Gardez le doigt appuyé, puis relâchez : le morceau reste sélectionné. Les cases permettent ensuite d'en choisir plusieurs. Tout sélectionner masque les cases et colore les titres.",
                "Glissez à droite pour « Lire ensuite », à gauche pour la fin de la file. L'action apparaît derrière le titre : elle ne se valide qu'en atteignant le bord ET en relâchant. Revenez en arrière pour annuler.",
                "Vos playlists, vos écoutes et la file « À suivre » se trouvent dans les onglets. Vous pourrez revoir ce guide dans le menu ⋮ du haut."
        };
        LinearLayout panel = Ui.vertical(this);
        panel.setPadding(d(19), d(17), d(19), d(18));
        panel.setBackground(Ui.gradient(0xFF2B343F, 0xFF17201F, 19, this));
        panel.addView(Ui.text(this, "DÉCOUVRIR ONDE  ·  " + (step + 1) + "/4", 11, Ui.ACCENT, true));
        panel.addView(Ui.gap(this, 10));
        panel.addView(Ui.text(this, titles[step], 20, Ui.TEXT, true));
        panel.addView(Ui.gap(this, 9));
        TextView description = Ui.text(this, messages[step], 13, Ui.TEXT, false);
        description.setLineSpacing(d(3), 1f);
        panel.addView(description);
        panel.addView(Ui.gap(this, 18));
        LinearLayout controls = Ui.horizontal(this);
        TextView skip = textButton("Passer", Ui.MUTED);
        controls.addView(skip, Ui.lp(this, -2, 43));
        skip.setOnClickListener(v -> dismissGuide(true));
        View space = new View(this);
        controls.addView(space, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView next = textButton(step == 3 ? "C'est parti  ✓" : "Suivant  ›", Ui.BG);
        next.setBackground(Ui.shape(Ui.ACCENT, 12, this));
        controls.addView(next, Ui.lp(this, -2, 43));
        next.setOnClickListener(v -> showGuideStep(step + 1));
        panel.addView(controls);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        pp.setMargins(d(17), d(15), d(17), d(23));
        guideOverlay.addView(panel, pp);
        panel.setTranslationY(d(24));
        panel.setAlpha(0f);
        panel.animate().translationY(0).alpha(1f).setDuration(240).start();
    }

    private void showMediaFilterDialog() {
        List<OptionSheet.Item> choices = OptionSheet.items();
        String[] labels = {"Tout afficher", "Musique uniquement", "Vidéos uniquement"};
        String[] details = {"Audio et vidéo dans la même bibliothèque", "Masquer les vidéos", "Masquer les fichiers audio"};
        for (int i = 0; i < labels.length; i++) {
            final int filter = i;
            choices.add(new OptionSheet.Item(mediaFilter == i ? "✓" : "☷", labels[i], details[i], () -> {
                mediaFilter = filter;
                getSharedPreferences("onde_settings", MODE_PRIVATE).edit().putInt("media_filter", filter).apply();
                render();
            }));
        }
        OptionSheet.show(this, "Type de média", "Filtrer aussi les playlists et l’historique", choices);
    }

    private boolean visibleMedia(Track track) {
        return mediaFilter == 0 || (mediaFilter == 1 && !track.isVideo) || (mediaFilter == 2 && track.isVideo);
    }

    private void showEqualizerDialog() {
        if (service == null || service.getCurrent() == null) {
            toast("Lance une musique ou une vidéo pour régler l'égaliseur");
            return;
        }
        String[] names = service.equalizerPresets();
        int selected = service.getEqualizerPreset();
        List<OptionSheet.Item> actions = OptionSheet.items();
        actions.add(new OptionSheet.Item("♫", "Basses et aigus",
                "Deux curseurs simples, effet immédiat", this::showSimpleEqualizer));
        actions.add(new OptionSheet.Item(selected == -2 ? "✓" : "≋", "Réglages avancés",
                "Toutes les fréquences avec réglages précis", this::showManualEqualizer));
        actions.add(new OptionSheet.Item("☷", "Mes profils enregistrés",
                "Retrouver, appliquer ou supprimer mes égalisations", this::showSavedEqualizers));
        actions.add(new OptionSheet.Item(selected == -1 ? "✓" : "≋", "Son d'origine",
                "Désactiver les réglages de fréquences", () -> {
            service.selectEqualizerPreset(-1); toast("Son d'origine restauré");
        }));
        for (int i = 0; i < names.length; i++) {
            int choice = i;
            actions.add(new OptionSheet.Item(choice == selected ? "✓" : "≋", names[i],
                    choice == selected ? "Préréglage actif" : "Appliquer ce préréglage", () -> {
                if (service != null) service.selectEqualizerPreset(choice);
                toast("Égaliseur : " + names[choice]);
            }));
        }
        boolean bass = service.isBassBoosted();
        actions.add(new OptionSheet.Item(bass ? "✓" : "≋", "Renforcement des basses",
                bass ? "Activé · toucher pour désactiver" : "Désactivé · toucher pour activer", () -> {
            if (service != null) service.setBassBoosted(!bass);
            toast(bass ? "Basses normales" : "Basses renforcées");
        }));
        OptionSheet.show(this, "Égaliseur et basses", names.length == 0
                ? "Les préréglages ne sont pas disponibles sur cet appareil" : "Choisis le son qui te ressemble", actions);
    }

    /** Simple tone controls map real Equalizer bands to lows and highs. No separate fake audio effect. */
    private void showSimpleEqualizer() {
        if (service == null || service.getCurrent() == null) {
            toast("Lance un média pour régler le son"); return;
        }
        if (service.getEqualizerBandCount() < 2) {
            new AlertDialog.Builder(this).setTitle("Réglage indisponible")
                    .setMessage("Ton téléphone ne propose pas au moins deux bandes audio indépendantes pour ce média.")
                    .setPositiveButton("Compris", null).show();
            return;
        }
        LinearLayout body = Ui.vertical(this);
        body.setPadding(d(23), d(18), d(23), d(6));
        body.addView(Ui.text(this, "PERSONNALISER LE SON", 11, Ui.ACCENT, true));
        body.addView(Ui.gap(this, 8));
        body.addView(Ui.text(this, "Deux réglages faciles. Les changements s'entendent immédiatement et restent actifs pour les prochaines musiques.",
                13, Ui.MUTED, false));
        body.addView(Ui.gap(this, 18));
        addToneSlider(body, 0, "Basses", "Graves, kicks et profondeur", "Basses atténuées", "Basses renforcées");
        body.addView(Ui.gap(this, 12));
        addToneSlider(body, 1, "Aigus", "Clarté, détails et cymbales", "Aigus atténués", "Aigus renforcés");
        body.addView(Ui.gap(this, 15));
        TextView help = Ui.text(this, "Le résultat dépend des capacités audio du téléphone et des écouteurs.",
                11, Ui.MUTED, false);
        body.addView(help);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Basses et aigus")
                .setView(body)
                .setNegativeButton("Fermer", null)
                .setNeutralButton("Avancé", null)
                .setPositiveButton("Enregistrer un profil", null)
                .create();
        dialog.setOnShowListener(v -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(w -> {
                dialog.dismiss(); showManualEqualizer();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w -> {
                dialog.dismiss(); showSaveEqProfile();
            });
        });
        dialog.show();
    }

    private void addToneSlider(LinearLayout body, int group, String title, String description,
                               String lowLabel, String highLabel) {
        LinearLayout heading = Ui.horizontal(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout captions = Ui.vertical(this);
        captions.addView(Ui.text(this, title, 18, Ui.TEXT, true));
        captions.addView(Ui.text(this, description, 11, Ui.MUTED, false));
        heading.addView(captions, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = Ui.text(this, "", 13, Ui.ACCENT, true);
        heading.addView(value);
        body.addView(heading);
        SeekBar slider = new SeekBar(this);
        slider.setMax(100);
        slider.setProgress(service.getToneLevel(group));
        slider.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        slider.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
        value.setText(toneLabel(slider.getProgress()));
        slider.setContentDescription(title + " : glisser pour modifier");
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                if (!user || service == null) return;
                service.setToneLevel(group, progress);
                value.setText(toneLabel(progress));
            }
        });
        body.addView(slider, Ui.lp(this, -1, 48));
        LinearLayout ends = Ui.horizontal(this);
        TextView low = Ui.text(this, lowLabel, 10, Ui.MUTED, false);
        ends.addView(low, new LinearLayout.LayoutParams(0, -2, 1));
        ends.addView(Ui.text(this, highLabel, 10, Ui.MUTED, false));
        body.addView(ends);
    }
    private String toneLabel(int percent) {
        int diff = percent - 50;
        return diff == 0 ? "Neutre" : (diff > 0 ? "+" : "") + diff * 2 + " %";
    }

    private static String bandFrequency(int hz) {
        return hz >= 1000 ? String.format(Locale.FRANCE, "%.1f kHz", hz / 1000.0) : hz + " Hz";
    }

    private void showManualEqualizer() {
        if (service == null || service.getCurrent() == null) {
            toast("Lance un média pour régler le son"); return;
        }
        int count = service.getEqualizerBandCount();
        if (count == 0) {
            new AlertDialog.Builder(this).setTitle("Égaliseur indisponible")
                    .setMessage("Le moteur audio de ce téléphone ne fournit pas de bandes réglables pour la lecture en cours.")
                    .setPositiveButton("Compris", null).show();
            return;
        }
        short[] range = service.getEqualizerRange();
        int low = range[0], high = range[1];
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = Ui.vertical(this);
        body.setPadding(d(22), d(16), d(22), d(20));
        body.addView(Ui.text(this, "ÉGALISEUR MANUEL", 12, Ui.ACCENT, true));
        body.addView(Ui.gap(this, 8));
        body.addView(Ui.text(this, "Fais glisser les curseurs : le son change immédiatement pendant la lecture. Enregistre ensuite ta courbe avec un nom.",
                13, Ui.MUTED, false));
        body.addView(Ui.gap(this, 18));
        for (int i = 0; i < count; i++) {
            final int band = i;
            LinearLayout line = Ui.horizontal(this);
            TextView name = Ui.text(this, bandFrequency(service.getEqualizerFrequency(i)), 13, Ui.TEXT, true);
            line.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView value = Ui.text(this, "", 12, Ui.ACCENT, true);
            line.addView(value);
            body.addView(line);
            SeekBar slider = new SeekBar(this);
            slider.setMax(Math.max(1, (high - low) / 10));
            slider.setProgress(Math.min(slider.getMax(), Math.max(0, (service.getEqualizerBandLevel(i) - low) / 10)));
            slider.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
            slider.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
            final int progressLow = low;
            value.setText(String.format(Locale.FRANCE, "%+.1f dB", (low + slider.getProgress() * 10) / 100.0));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onStartTrackingTouch(SeekBar bar) { }
                @Override public void onStopTrackingTouch(SeekBar bar) { }
                @Override public void onProgressChanged(SeekBar bar, int position, boolean fromUser) {
                    if (!fromUser || service == null) return;
                    short level = (short) (progressLow + position * 10);
                    service.setCustomBandLevel(band, level);
                    value.setText(String.format(Locale.FRANCE, "%+.1f dB", level / 100.0));
                }
            });
            body.addView(slider, Ui.lp(this, -1, 46));
            body.addView(Ui.gap(this, 7));
        }
        TextView note = Ui.text(this, "Le nombre de bandes et les limites de gain sont fixés par Android et peuvent varier selon l'appareil.",
                11, Ui.MUTED, false);
        body.addView(note);
        scroll.addView(body);
        final AlertDialog dialog = new AlertDialog.Builder(this).setView(scroll)
                .setNegativeButton("Fermer", (x, y) -> { })
                .setNeutralButton("Remettre à zéro", null)
                .setPositiveButton("Enregistrer", null).create();
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> showSaveEqProfile());
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                if (service != null) service.resetCustomEqualizer();
                dialog.dismiss();
                showManualEqualizer();
            });
        });
        dialog.show();
    }

    private void showSaveEqProfile() {
        if (service == null) return;
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setTextColor(Ui.TEXT);
        name.setHint("Ex. Mes basses, Acoustique, Tekno…");
        LinearLayout wrapper = Ui.vertical(this);
        wrapper.setPadding(d(18), d(8), d(18), d(6));
        wrapper.addView(name);
        new AlertDialog.Builder(this).setTitle("Enregistrer un profil")
                .setView(wrapper).setNegativeButton("Annuler", null)
                .setPositiveButton("Enregistrer", (dialog, which) -> {
                    String title = name.getText().toString().trim();
                    if (title.isEmpty()) { toast("Donne un nom au profil"); return; }
                    if (service != null) { service.saveEqualizerProfile(title); toast("Profil « " + title + " » enregistré"); }
                }).show();
    }

    private void showSavedEqualizers() {
        if (service == null) return;
        String[] names = service.namedEqualizerProfiles();
        if (names.length == 0) { toast("Aucun profil enregistré pour l'instant"); return; }
        List<OptionSheet.Item> actions = OptionSheet.items();
        for (String name : names) {
            actions.add(new OptionSheet.Item("≋", name, "Appliquer ou supprimer ce profil", () -> {
                new AlertDialog.Builder(this).setTitle(name)
                        .setItems(new String[]{"Appliquer ce profil", "Supprimer ce profil"}, (dialog, choice) -> {
                            if (service == null) return;
                            if (choice == 0) { service.loadEqualizerProfile(name); toast("Profil appliqué : " + name); }
                            else new AlertDialog.Builder(this).setTitle("Supprimer « " + name + " » ?")
                                    .setNegativeButton("Annuler", null)
                                    .setPositiveButton("Supprimer", (d, w) -> { if (service != null) service.removeEqualizerProfile(name); })
                                    .show();
                        }).show();
            }));
        }
        OptionSheet.show(this, "Mes égaliseurs", "Profils mémorisés sur cet appareil", actions);
    }

    private void showSleepDialog() {
        if (service == null) { toast("Démarre une musique pour activer le minuteur"); return; }
        int remaining = service.getSleepMinutesRemaining();
        int[] minutes = {0, 15, 30, 45, 60, 90};
        List<OptionSheet.Item> actions = OptionSheet.items();
        for (int value : minutes) {
            String title = value == 0 ? "Désactiver le minuteur" : "Dans " + value + " minutes";
            String detail = value == 0 ? "La musique continue normalement" : "La musique sera mise en pause";
            actions.add(new OptionSheet.Item(value == 0 ? "✕" : "⏱", title, detail, () -> {
                if (service != null) service.setSleepMinutes(value);
                updateSleepBubble();
                toast(value == 0 ? "Minuteur désactivé" : "Pause dans " + value + " minutes");
            }));
        }
        OptionSheet.show(this, "Minuteur de sommeil", remaining > 0
                ? "Encore " + remaining + " minute(s) avant la pause" : "Mets ta musique en veille automatiquement", actions);
    }

    private void showLoading() {
        content.removeAllViews();
        showEmpty(content, "Exploration de vos morceaux…", "Lecture du catalogue musical Android en cours.", null, null);
    }
    private void showPermissionScreen() {
        content.removeAllViews();
        showEmpty(content, "Autorise l'accès à ta musique", "Onde utilise la bibliothèque audio Android pour afficher les morceaux présents sur ce téléphone.",
                "Autoriser l'accès", this::requestAudioPermission);
    }
    private void showEmpty(FrameLayout parent, String title, String description, String buttonText, Runnable action) {
        LinearLayout stack = Ui.vertical(this);
        stack.setGravity(Gravity.CENTER);
        Ui.pad(stack, this, 28, 20);
        TextView symbol = Ui.text(this, "♫", 54, Ui.ACCENT, true);
        symbol.setGravity(Gravity.CENTER);
        stack.addView(symbol, Ui.lp(this, -1, 80));
        stack.addView(Ui.gap(this, 12));
        TextView t = Ui.text(this, title, 19, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        stack.addView(t, Ui.lp(this, -1, -2));
        stack.addView(Ui.gap(this, 9));
        TextView desc = Ui.text(this, description, 13, Ui.MUTED, false);
        desc.setGravity(Gravity.CENTER);
        stack.addView(desc, Ui.lp(this, -1, -2));
        if (buttonText != null && action != null) {
            stack.addView(Ui.gap(this, 24));
            TextView button = textButton(buttonText, Ui.ACCENT);
            button.setGravity(Gravity.CENTER);
            stack.addView(button, Ui.lp(this, -2, -2));
            button.setOnClickListener(v -> action.run());
        }
        parent.addView(stack, new FrameLayout.LayoutParams(-1, -1));
    }
    private void render() {
        // Rebuilds leave selection mode; restore shared chrome before rendering a new screen.
        if (headerMotion != null) {
            headerMotion.dispose();
            headerMotion = null;
        }
        headerScrolled = false;
        pageHeading = null; pageTypes = null; pageToolbar = null; pageHint = null;
        setSelectionHeaderCompact(false);
        setScrolledHeader(false);
        playlistMembershipCounts = store.membershipCounts();
        content.removeAllViews();
        activeTrackList = null;
        if (!hasScanned) return;
        if (screen.equals("Titres")) drawTracks(filter(library), "Votre bibliothèque", "fichiers disponibles", null);
        else if (screen.equals("Albums")) {
            if (detailAlbum == null) drawAlbums();
            else drawTracks(filter(albumTracks(detailAlbum)), detailName, "album", this::clearAndRender);
        } else if (screen.equals("Artistes")) {
            if (detailArtist == null) drawArtists();
            else drawTracks(filter(artistTracks(detailArtist)), detailName, "artiste", this::clearAndRender);
        } else if (screen.equals("Playlists")) {
            if (detailPlaylist == null) drawPlaylists();
            else drawPlaylistDetail();
        } else if (screen.equals("Écoutés")) drawHistory();
        else drawQueue();
    }
    /** Selection stays compact; the list's own scroll can also hide the chrome. */
    private void setSelectionHeaderCompact(boolean active) {
        if (brandHeader != null) brandHeader.setVisibility(active || headerScrolled ? View.GONE : View.VISIBLE);
        if (tabHeader != null) tabHeader.setVisibility(headerScrolled ? View.GONE : View.VISIBLE);
        if (headerSubtitle != null) headerSubtitle.setVisibility(View.GONE);
    }

    /** Only the gesture controller animates. This method commits visibility
     * once; it also safely resets chrome on screen/theme rebuilds. */
    private void setScrolledHeader(boolean compact) {
        if (!screen.equals("Titres")) compact = false;
        headerScrolled = compact;
        if (brandHeader != null) brandHeader.setVisibility(compact ? View.GONE : View.VISIBLE);
        if (tabHeader != null) tabHeader.setVisibility(compact ? View.GONE : View.VISIBLE);
        if (brandTitleStack != null) {
            brandTitleStack.animate().cancel();
            brandTitleStack.setAlpha(1f);
            brandTitleStack.setTranslationY(0f);
            brandTitleStack.setVisibility(View.VISIBLE);
        }
        for (View v : new View[]{pageHeading, pageTypes, pageToolbar, pageHint}) {
            if (v == null) continue;
            v.animate().cancel();
            v.setAlpha(1f);
            v.setTranslationY(0f);
            v.setVisibility(compact ? View.GONE : View.VISIBLE);
        }
    }

    /** Search is self-contained: results, clearing and playback live in the same window.
     * The underlying library is never left invisibly filtered after dismissing the dialog. */
    private void openSearchDialog() {
        if (search != null && search.length() > 0) search.setText("");
        LinearLayout container = Ui.vertical(this);
        container.setPadding(d(18), d(5), d(18), d(8));
        LinearLayout queryRow = Ui.horizontal(this);
        queryRow.setPadding(d(12), d(4), d(7), d(4));
        queryRow.setBackground(Ui.outlined(Ui.SURFACE_ALT, 0xFF4C5966, 15, this));
        TextView lens = Ui.text(this, "⌕", 25, Ui.ACCENT, true);
        lens.setGravity(Gravity.CENTER);
        queryRow.addView(lens, Ui.lp(this, 30, 43));
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setTextSize(16);
        field.setTextColor(Ui.TEXT);
        field.setHintTextColor(Ui.MUTED);
        field.setBackgroundColor(Color.TRANSPARENT);
        field.setHint("Titre, artiste, album, vidéo…");
        field.setPadding(d(9), 0, d(4), 0);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        queryRow.addView(field, new LinearLayout.LayoutParams(0, d(49), 1f));
        TextView clear = Ui.icon(this, "✕", 37, 17, Ui.TEXT, Ui.SURFACE);
        clear.setContentDescription("Effacer la recherche");
        LinearLayout.LayoutParams clearP = Ui.lp(this, 37, 37);
        clearP.leftMargin = d(9);
        queryRow.addView(clear, clearP);
        container.addView(queryRow);

        TextView count = Ui.text(this, "", 12, Ui.MUTED, false);
        count.setPadding(d(5), d(13), 0, d(9));
        container.addView(count);

        FrameLayout resultsFrame = new FrameLayout(this);
        ListView resultsList = new ListView(this);
        resultsList.setDivider(null);
        resultsList.setSelector(android.R.color.transparent);
        resultsList.setClipToPadding(false);
        resultsList.setPadding(0, d(3), 0, d(8));
        resultsFrame.addView(resultsList, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = Ui.text(this, "Aucun titre correspondant.\nEssaie un autre mot-clé.", 13, Ui.MUTED, false);
        empty.setGravity(Gravity.CENTER);
        resultsFrame.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        resultsList.setEmptyView(empty);
        int available = (int)(getResources().getDisplayMetrics().heightPixels * .43f);
        container.addView(resultsFrame, new LinearLayout.LayoutParams(-1, Math.max(d(175), Math.min(d(390), available))));
        TextView help = Ui.text(this, "Touchez un résultat pour lancer la lecture.", 11, Ui.MUTED, false);
        help.setPadding(d(5), d(8), 0, d(2));
        container.addView(help);

        final List<Track> results = new ArrayList<>();
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return results.size(); }
            @Override public Object getItem(int pos) { return results.get(pos); }
            @Override public long getItemId(int pos) { return results.get(pos).id; }
            @Override public View getView(int pos, View reused, ViewGroup parent) {
                Track track = results.get(pos);
                LinearLayout item;
                ImageView image;
                TextView title, detail, time;
                if (reused instanceof LinearLayout && reused.getTag() instanceof View[]) {
                    item = (LinearLayout) reused;
                    View[] holder = (View[]) item.getTag();
                    image = (ImageView)holder[0];
                    title = (TextView)holder[1];
                    detail = (TextView)holder[2];
                    time = (TextView)holder[3];
                } else {
                    item = Ui.horizontal(MainActivity.this);
                    item.setPadding(d(9), d(8), d(9), d(8));
                    image = cover(45, 10);
                    item.addView(image, Ui.lp(MainActivity.this, 45, 45));
                    LinearLayout labels = Ui.vertical(MainActivity.this);
                    labels.setPadding(d(12), 0, 0, 0);
                    title = Ui.text(MainActivity.this, "", 14, Ui.TEXT, true);
                    title.setSingleLine(true);
                    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    detail = Ui.text(MainActivity.this, "", 11, Ui.MUTED, false);
                    detail.setSingleLine(true);
                    detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    labels.addView(title);
                    labels.addView(Ui.gap(MainActivity.this, 5));
                    labels.addView(detail);
                    item.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
                    time = Ui.text(MainActivity.this, "", 11, Ui.MUTED, false);
                    time.setGravity(Gravity.CENTER);
                    time.setPadding(d(10), 0, 0, 0);
                    item.addView(time);
                    item.setTag(new View[]{image, title, detail, time});
                }
                title.setText(track.title);
                detail.setText(track.artist + (track.isVideo ? "  ·  Vidéo" : "  ·  " + track.album));
                time.setText(Track.time(track.durationMs));
                Artwork.bind(MainActivity.this, image, track);
                return item;
            }
        };
        resultsList.setAdapter(adapter);
        Runnable refreshResults = () -> {
            String query = field.getText().toString().trim();
            results.clear();
            for (Track t : library) {
                if (visibleMedia(t) && SearchRules.matches(query, t.title, t.artist, t.album)) results.add(t);
            }
            results.sort(Comparator.comparing(t -> t.title.toLowerCase(Locale.ROOT)));
            adapter.notifyDataSetChanged();
            resultsList.setSelection(0);
            count.setText(results.size() + (results.size() > 1 ? " résultats" : " résultat") +
                    (query.isEmpty() ? " dans la bibliothèque" : " pour « " + field.getText().toString().trim() + " »"));
            clear.setVisibility(query.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        };
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refreshResults.run(); }
            @Override public void afterTextChanged(Editable e) {}
        });
        clear.setOnClickListener(v -> {
            field.setText("");
            field.requestFocus();
        });
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Rechercher")
                .setView(container)
                .setNegativeButton("Fermer", null)
                .create();
        resultsList.setOnItemClickListener((parent, row, position, id) -> {
            if (position < 0 || position >= results.size()) return;
            List<Track> matches = new ArrayList<>(results);
            dialog.dismiss();
            startPlaying(matches, position);
        });
        refreshResults.run();
        dialog.show();
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        dialog.getWindow().setAttributes(params);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE |
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        field.requestFocus();
    }

    private void clearAndRender() { clearDetail(); render(); }
    private boolean matches(String... values) {
        String needle = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return true;
        for (String value : values) if (value != null && value.toLowerCase(Locale.ROOT).contains(needle)) return true;
        return false;
    }
    private List<Track> filterPreservingOrder(List<Track> items) {
        List<Track> result = new ArrayList<>();
        for (Track track : items)
            if (visibleMedia(track) && matches(track.title, track.artist, track.album)) result.add(track);
        return result;
    }
    private List<Track> filter(List<Track> items) {
        List<Track> result = filterPreservingOrder(items);
        switch (sorting) {
            case 1: result.sort(Comparator.comparing(t -> t.artist.toLowerCase(Locale.ROOT))); break;
            case 2: result.sort(Comparator.comparing(t -> t.album.toLowerCase(Locale.ROOT))); break;
            case 3: result.sort(Comparator.comparingLong(t -> t.durationMs)); break;
            case 4: result.sort((a, b) -> Long.compare(b.dateAddedSeconds, a.dateAddedSeconds)); break;
            case 5: result.sort((a, b) -> Long.compare(a.dateAddedSeconds, b.dateAddedSeconds)); break;
            default:
                if (detailAlbum != null && screen.equals("Albums")) {
                    result.sort(Comparator.comparingInt((Track t) -> t.trackNumber == 0 ? Integer.MAX_VALUE : t.trackNumber)
                            .thenComparing(t -> t.title.toLowerCase(Locale.ROOT)));
                } else result.sort(Comparator.comparing((Track t) -> AlphabetRules.sortKey(t.title))
                        .thenComparingLong(t -> t.id));
        }
        return result;
    }
    private void showSortDialog() {
        String[] options = {"Titre (A → Z) / ordre de l’album", "Artiste (A → Z)", "Album (A → Z)",
                "Durée (courts en premier)", "Ajouts récents", "Plus anciens ajouts"};
        List<OptionSheet.Item> entries = OptionSheet.items();
        for (int i = 0; i < options.length; i++) {
            final int option = i;
            entries.add(new OptionSheet.Item(i == sorting ? "✓" : "☷", options[i],
                    i == sorting ? "Tri actuellement sélectionné" : "Trier toute la bibliothèque", () -> {
                sorting = option;
                getSharedPreferences("onde_settings", MODE_PRIVATE).edit().putInt("track_sort", sorting).apply();
                render();
            }));
        }
        OptionSheet.show(this, "Trier les morceaux", "Choisir un ordre d'affichage", entries);
    }
    private List<Track> albumTracks(long albumId) {
        List<Track> tracks = new ArrayList<>();
        for (Track t : library) if (t.albumId == albumId) tracks.add(t);
        return tracks;
    }
    private List<Track> artistTracks(String artist) {
        List<Track> tracks = new ArrayList<>();
        for (Track t : library) if (t.artist.equals(artist)) tracks.add(t);
        return tracks;
    }
    private List<Track> resolveIds(List<Long> ids) {
        List<Track> tracks = new ArrayList<>();
        for (Long id : ids) { Track t = MusicRepository.get(id); if (t != null) tracks.add(t); }
        return tracks;
    }
    private LinearLayout page() {
        LinearLayout box = Ui.vertical(this);
        content.addView(box, new FrameLayout.LayoutParams(-1, -1));
        return box;
    }
    private void heading(LinearLayout target, String title, String sub, Runnable back) {
        LinearLayout line = Ui.horizontal(this);
        line.setPadding(d(20), d(12), d(20), d(12));
        if (back != null) {
            TextView arrow = Ui.icon(this, "‹", 38, 32, Ui.TEXT, Ui.SURFACE);
            arrow.setContentDescription("Retour");
            arrow.setOnClickListener(v -> back.run());
            LinearLayout.LayoutParams arrParams = Ui.lp(this, 38, 38);
            arrParams.rightMargin = d(12);
            line.addView(arrow, arrParams);
        }
        LinearLayout titles = Ui.vertical(this);
        TextView large = Ui.text(this, title == null ? "" : title, 21, Ui.TEXT, true);
        large.setSingleLine(true);
        large.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(large);
        TextView small = Ui.text(this, sub == null ? "" : sub, 12, Ui.MUTED, false);
        titles.addView(Ui.gap(this, 5));
        titles.addView(small);
        line.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        target.addView(line);
        if (screen.equals("Titres") && back == null) pageHeading = line;
    }
    private void toolbar(LinearLayout parent, List<Track> items) {
        if (items.isEmpty()) return;
        LinearLayout bar = Ui.horizontal(this);
        bar.setPadding(d(20), 0, d(20), d(10));
        LinearLayout play = actionChip("▶", "Tout lire", true);
        play.setOnClickListener(v -> {
            if (service != null) service.setShuffle(false);
            startPlaying(items, 0);
        });
        bar.addView(play);
        LinearLayout shuffled = actionChip("⤨", "Mélanger", false);
        LinearLayout.LayoutParams p = Ui.lp(this, -2, -2); p.leftMargin = d(10);
        bar.addView(shuffled, p);
        shuffled.setOnClickListener(v -> playShuffled(items));
        if (screen.equals("Titres") || screen.equals("Albums") || screen.equals("Artistes")) {
            TextView sort = Ui.icon(this, "☷", 42, 22, Ui.TEXT, Ui.SURFACE);
            sort.setContentDescription("Trier les morceaux");
            LinearLayout.LayoutParams sp = Ui.lp(this, -2, -2);
            sp.leftMargin = d(10);
            bar.addView(sort, sp);
            sort.setOnClickListener(v -> showSortDialog());
        }
        parent.addView(bar);
        if (screen.equals("Titres")) pageToolbar = bar;
    }
    private void drawTracks(List<Track> tracks, String title, String sub, Runnable back) {
        LinearLayout layout = page();
        if (screen.equals("Titres") && back == null) {
            // A single compact title line; avoid duplicating the app's large brand header.
            LinearLayout line = Ui.horizontal(this);
            line.setPadding(d(20), d(2), d(20), d(5));
            line.addView(Ui.text(this, "Vos titres", 16, Ui.TEXT, true));
            line.addView(Ui.gap(this, 9));
            line.addView(Ui.text(this, "·  " + tracks.size() + " fichiers", 11, Ui.MUTED, false));
            layout.addView(line);
            pageHeading = line;
        } else heading(layout, title, tracks.size() + " " + sub, back);
        if (screen.equals("Titres") || screen.equals("Playlists") || screen.equals("Écoutés")) {
            LinearLayout types = Ui.horizontal(this);
            types.setPadding(d(14), d(4), d(14), d(11));
            String[] labels = {"Tout", "Musique", "Vidéos"};
            for (int i = 0; i < labels.length; i++) {
                final int f = i;
                TextView chip = Ui.text(this, labels[i], 12, f == mediaFilter ? Ui.BG : Ui.MUTED, true);
                chip.setGravity(Gravity.CENTER);
                chip.setPadding(d(13), d(9), d(13), d(9));
                chip.setBackground(Ui.shape(f == mediaFilter ? Ui.ACCENT : Ui.SURFACE, 15, this));
                LinearLayout.LayoutParams cp = Ui.lp(this, -2, -2);
                cp.rightMargin = d(11);
                types.addView(chip, cp);
                chip.setOnClickListener(v -> {
                    mediaFilter = f;
                    getSharedPreferences("onde_settings", MODE_PRIVATE).edit().putInt("media_filter", f).apply();
                    render();
                });
            }
            layout.addView(types);
            if (screen.equals("Titres")) pageTypes = types;
        }
        toolbar(layout, tracks);
        if (screen.equals("Titres") && !tracks.isEmpty()) {
            TextView hint = Ui.text(this, "Toucher pour lire   ·   Glisser → lire ensuite   ·   ← file d’attente", 11, Ui.MUTED, false);
            hint.setPadding(d(20), d(1), d(10), d(12));
            layout.addView(hint);
            pageHint = hint;
        }
        if (tracks.isEmpty()) {
            FrameLayout empty = new FrameLayout(this);
            layout.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1f));
            showEmpty(empty, "Aucun morceau", "Les titres présents sur le téléphone apparaîtront ici.", null, null);
            return;
        }
        ListView list = new ListView(this) {
            // Receive every vertical gesture even if a track row has its own
            // swipe handler. We never intercept or consume touch events.
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                if (headerMotion != null && activeTrackList == this)
                    headerMotion.onTouch(event);
                return super.dispatchTouchEvent(event);
            }
        };
        list.setDivider(null);
        list.setSelector(android.R.color.transparent);
        list.setClipToPadding(false);
        list.setPadding(d(12), 0, d(12), d(15));
        TrackAdapter adapter = new TrackAdapter(tracks, false);
        list.setAdapter(adapter);
        activeTrackList = list;
        list.setOnItemClickListener((parent, v, pos, id) -> startPlaying(tracks, pos));
        FrameLayout listFrame = new FrameLayout(this);
        if (screen.equals("Titres")) {
            final boolean alphabetEnabled = sorting == 0;
            final AlphabetIndexView az = alphabetEnabled ? new AlphabetIndexView(this) : null;
            final int[] letterPositions;
            if (az != null) {
                List<String> titles = new ArrayList<>();
                for (Track t : tracks) titles.add(t.title);
                letterPositions = AlphabetRules.firstPositions(titles);
                // Keep row actions away from the A-Z gesture rail.
                list.setPadding(d(12), 0, d(40), d(15));
                az.setListener(letter -> {
                    int index = AlphabetRules.nearestPosition(letterPositions, letter);
                    if (index >= 0) list.setSelectionFromTop(index, 0);
                });
            }
            list.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
                @Override public void onScrollStateChanged(android.widget.AbsListView view, int state) {
                    if (headerMotion != null && activeTrackList == list)
                        headerMotion.onScrollStateChanged(state);
                }
                @Override public void onScroll(android.widget.AbsListView view, int first, int visible, int total) {
                    // A viewport relayout must never trigger another header relayout.
                    if (az != null && first >= 0 && first < tracks.size())
                        az.highlight(AlphabetRules.group(tracks.get(first).title));
                }
            });
            if (az != null) {
                FrameLayout.LayoutParams rail = new FrameLayout.LayoutParams(d(29), -1, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                rail.rightMargin = d(3);
                listFrame.addView(list, new FrameLayout.LayoutParams(-1, -1));
                listFrame.addView(az, rail);
            } else listFrame.addView(list, new FrameLayout.LayoutParams(-1, -1));
        } else listFrame.addView(list, new FrameLayout.LayoutParams(-1, -1));
        enableBulkSelection(layout, list, tracks, adapter);
        layout.addView(listFrame, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (screen.equals("Titres")) {
            headerMotion = new HeaderMotionController(this, list, listFrame,
                    new View[]{brandHeader, tabHeader, pageHeading, pageTypes, pageToolbar, pageHint},
                    headerScrolled, new HeaderMotionController.Delegate() {
                @Override public void applyImmediate(boolean compact) { setScrolledHeader(compact); }
                @Override public boolean isCurrent(ListView candidate) {
                    return activeTrackList == candidate && screen.equals("Titres");
                }
            });
        }
    }
    private void playQueuedTrack(int position) {
        if (service == null) return;
        List<Track> waiting = service.upcoming();
        if (position < 0 || position >= waiting.size()) return;
        Track track = waiting.get(position);
        service.removeUpcoming(position);
        service.playNext(track);
        service.next();
        if (screen.equals("À suivre")) render();
        if (track.isVideo) root.postDelayed(this::showFullPlayer, 100);
    }

    private void playImmediatelyAfterCurrent(Track track) {
        if (service == null) { toast("Lecteur en cours de connexion"); return; }
        try { startService(new Intent(this, PlaybackService.class)); }
        catch (RuntimeException e) { toast("Impossible de démarrer le lecteur"); return; }
        service.playNext(track);
        toast("À suivre : " + track.title);
        if (screen.equals("À suivre")) render();
    }

    private void appendToQueue(Track track) {
        if (service == null) { toast("Lecteur en cours de connexion"); return; }
        try { startService(new Intent(this, PlaybackService.class)); }
        catch (RuntimeException e) { toast("Impossible de démarrer le lecteur"); return; }
        service.enqueue(track);
        toast("Ajouté à la fin de la file : " + track.title);
        if (screen.equals("À suivre")) render();
    }

    private void startPlaying(List<Track> tracks, int position) {
        if (service == null) { toast("Lecteur en cours de connexion. Réessaie."); return; }
        try { startService(new Intent(this, PlaybackService.class)); }
        catch (Exception e) { toast("Impossible de démarrer le lecteur : " + e.getMessage()); return; }
        service.playTracks(tracks, position);
        closeKeyboard();
        refreshPlayer();
        if (position >= 0 && position < tracks.size() && tracks.get(position).isVideo)
            root.postDelayed(this::showFullPlayer, 90);
    }
    private static String listeningTime(long millis) {
        long minutes = Math.max(0, millis / 60000L);
        if (millis > 0 && minutes == 0) return "< 1 min";
        if (minutes < 60) return minutes + " min";
        long hours = minutes / 60;
        return hours + " h " + (minutes % 60) + " min";
    }

    private LinearLayout statTile(String label, String number, String helper, int tint) {
        LinearLayout tile = Ui.vertical(this);
        tile.setPadding(d(14), d(15), d(8), d(13));
        tile.setBackground(Ui.gradient(0xFF2B333F, 0xFF1B2430, 16, this));
        tile.addView(Ui.text(this, label.toUpperCase(Locale.FRANCE), 10, Ui.MUTED, true));
        tile.addView(Ui.gap(this, 10));
        TextView value = Ui.text(this, number, 23, tint, true);
        value.setSingleLine(true);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tile.addView(value);
        tile.addView(Ui.gap(this, 6));
        tile.addView(Ui.text(this, helper, 11, Ui.MUTED, false));
        return tile;
    }
    private void statPair(LinearLayout layout, LinearLayout left, LinearLayout right) {
        LinearLayout line = Ui.horizontal(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        line.addView(left, lp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -2, 1f);
        rp.leftMargin = d(9);
        line.addView(right, rp);
        LinearLayout.LayoutParams rowLp = Ui.lp(this, -1, -2);
        rowLp.bottomMargin = d(9);
        layout.addView(line, rowLp);
    }
    private void addAchievement(LinearLayout parent, String icon, String name, String condition, boolean unlocked) {
        LinearLayout item = Ui.vertical(this);
        item.setPadding(d(11), d(12), d(11), d(12));
        item.setBackground(Ui.outlined(unlocked ? 0xFF304536 : 0xFF222631,
                unlocked ? 0xFF658F50 : 0xFF363D48, 14, this));
        TextView symbol = Ui.text(this, icon, 24, unlocked ? Ui.ACCENT : Ui.MUTED, true);
        item.addView(symbol);
        item.addView(Ui.gap(this, 7));
        TextView title = Ui.text(this, name, 12, unlocked ? Ui.TEXT : Ui.MUTED, true);
        title.setSingleLine(true);
        item.addView(title);
        item.addView(Ui.gap(this, 5));
        TextView detail = Ui.text(this, unlocked ? "Débloqué ✓" : condition, 10, unlocked ? Ui.ACCENT : Ui.MUTED, false);
        detail.setMaxLines(2);
        item.addView(detail);
        LinearLayout.LayoutParams ip = Ui.lp(this, 132, 113);
        ip.rightMargin = d(9);
        parent.addView(item, ip);
    }
    private void drawListeningDashboard(LinearLayout container) {
        LibraryStore.Statistics stats = store.statistics();
        LinearLayout dashboard = Ui.vertical(this);
        dashboard.setPadding(d(18), d(9), d(18), d(13));
        LinearLayout hero = Ui.vertical(this);
        hero.setPadding(d(19), d(19), d(19), d(17));
        hero.setBackground(Ui.gradient(0xFF384936, 0xFF252E40, 20, this));
        hero.addView(Ui.text(this, "VOTRE UNIVERS MUSICAL", 10, Ui.ACCENT, true));
        hero.addView(Ui.gap(this, 7));
        int xp = (int) Math.min(Integer.MAX_VALUE, stats.plays * 5 + stats.listenedMs / 60000L * 2);
        int level = Math.max(1, (int)Math.floor((Math.sqrt(1d + 8d * xp / 100d) - 1d) / 2d) + 1);
        long floor = 100L * (level-1) * level / 2;
        long ceiling = 100L * level * (level+1) / 2;
        hero.addView(Ui.text(this, "Niveau " + level + "  ·  " + xp + " XP", 25, Ui.TEXT, true));
        hero.addView(Ui.gap(this, 6));
        hero.addView(Ui.text(this, (ceiling - xp) + " XP avant le prochain niveau", 12, 0xFFDAE8D7, false));
        hero.addView(Ui.gap(this, 15));
        android.widget.ProgressBar levelBar = new android.widget.ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        levelBar.setMax(1000);
        levelBar.setProgress((int)Math.max(0, Math.min(1000, 1000L * (xp - floor) / Math.max(1, ceiling - floor))));
        levelBar.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        levelBar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF637468));
        hero.addView(levelBar, Ui.lp(this, -1, 7));
        dashboard.addView(hero, Ui.lp(this, -1, -2));
        dashboard.addView(Ui.gap(this, 14));
        statPair(dashboard,
                statTile("Temps total", listeningTime(stats.listenedMs), "Temps réellement écouté", Ui.ACCENT),
                statTile("Titres lancés", String.valueOf(stats.plays), "Lectures enregistrées", Ui.TEXT));
        statPair(dashboard,
                statTile("Aujourd'hui", listeningTime(stats.todayMs), "Votre session du jour", Ui.TEXT),
                statTile("Série active", stats.streakDays + " jours", "Jours consécutifs", Ui.PURPLE));
        LinearLayout challenge = Ui.vertical(this);
        challenge.setPadding(d(16), d(14), d(16), d(14));
        challenge.setBackground(Ui.outlined(Ui.SURFACE, 0xFF475E4A, 16, this));
        challenge.addView(Ui.text(this, "DÉFI DU JOUR  ·  30 MINUTES", 11, Ui.ACCENT, true));
        challenge.addView(Ui.gap(this, 6));
        long dailyMinutes = stats.todayMs / 60000L;
        challenge.addView(Ui.text(this, dailyMinutes >= 30 ? "Défi accompli ! ✦" : dailyMinutes + " / 30 min écoutées",
                14, Ui.TEXT, true));
        challenge.addView(Ui.gap(this, 11));
        android.widget.ProgressBar goal = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        goal.setMax(1000);
        goal.setProgress((int) Math.min(1000L, stats.todayMs * 1000L / (30L * 60000L)));
        goal.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        goal.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF3D4654));
        challenge.addView(goal, Ui.lp(this, -1, 6));
        LinearLayout.LayoutParams cp = Ui.lp(this, -1, -2);
        cp.bottomMargin = d(13);
        dashboard.addView(challenge, cp);

        LinearLayout weekly = Ui.vertical(this);
        weekly.setPadding(d(14), d(17), d(14), d(8));
        weekly.setBackground(Ui.shape(Ui.SURFACE, 17, this));
        weekly.addView(Ui.text(this, "VOS 7 DERNIERS JOURS", 11, Ui.MUTED, true));
        weekly.addView(Ui.gap(this, 5));
        weekly.addView(Ui.text(this, listeningTime(stats.weekMs) + "  ·  " + stats.uniqueTracks + " titres explorés", 18, Ui.TEXT, true));
        weekly.addView(new ListeningWeekView(this, store.lastSevenDays()), Ui.lp(this, -1, 174));
        dashboard.addView(weekly);
        dashboard.addView(Ui.gap(this, 20));
        dashboard.addView(Ui.text(this, "Vos trophées", 19, Ui.TEXT, true));
        dashboard.addView(Ui.gap(this, 4));
        dashboard.addView(Ui.text(this, "Débloquez des badges en écoutant vos morceaux", 12, Ui.MUTED, false));
        dashboard.addView(Ui.gap(this, 12));
        HorizontalScrollView badgeScroll = new HorizontalScrollView(this);
        badgeScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout badges = Ui.horizontal(this);
        addAchievement(badges, "♫", "Première note", "1 écoute", stats.plays>=1);
        addAchievement(badges, "✦", "Mélomane", "100 écoutes", stats.plays>=100);
        addAchievement(badges, "◷", "Marathon", "5 h d'écoute", stats.listenedMs>=18000000L);
        addAchievement(badges, "↻", "Régulier", "3 jours de suite", stats.streakDays>=3);
        addAchievement(badges, "☆", "Explorateur", "50 titres distincts", stats.uniqueTracks>=50);
        badgeScroll.addView(badges);
        dashboard.addView(badgeScroll);
        dashboard.addView(Ui.gap(this, 19));
        TextView info = Ui.text(this, "Le temps réel d'écoute est comptabilisé depuis la V3. Les anciennes lectures sont conservées.",
                11, Ui.MUTED, false);
        info.setMaxLines(3);
        dashboard.addView(info);
        dashboard.addView(Ui.gap(this, 24));
        container.addView(dashboard);
    }

    private void drawHistory() {
        List<Track> tracks = filterPreservingOrder(resolveIds(topHistory ? store.mostPlayedIds(250) : store.recentIds(250)));
        LinearLayout layout = page();
        ListView list = listView();
        LinearLayout header = Ui.vertical(this);
        heading(header, "Mes écoutes", "Votre activité musicale, en privé", null);
        drawListeningDashboard(header);
        LinearLayout modes = Ui.horizontal(this);
        modes.setPadding(d(18), 0, d(18), d(12));
        for (int i = 0; i < 2; i++) {
            final boolean top = i == 1;
            TextView tab = textButton(top ? "Les plus écoutés" : "Écoutes récentes", topHistory == top ? Ui.ACCENT : Ui.MUTED);
            tab.setBackground(Ui.shape(topHistory == top ? 0xFF2B3C30 : Ui.SURFACE, 13, this));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, d(43), 1f);
            if (i == 1) lp.leftMargin = d(8);
            modes.addView(tab, lp);
            tab.setOnClickListener(v -> { topHistory = top; render(); });
        }
        header.addView(modes);
        if (tracks.isEmpty()) {
            TextView empty = Ui.text(this, "Écoute un morceau pour voir ton classement apparaître ici.", 12, Ui.MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(d(20), d(22), d(20), d(24));
            header.addView(empty);
        } else {
            TextView hint = Ui.text(this, "Glisser vers la droite → écouter ensuite", 11, Ui.MUTED, false);
            hint.setPadding(d(20), d(3), d(15), d(10));
            header.addView(hint);
        }
        list.addHeaderView(header, null, false);
        TrackAdapter historyAdapter = new TrackAdapter(tracks, false, true);
        list.setAdapter(historyAdapter);
        activeTrackList = list;
        list.setOnItemClickListener((p, v, index, id) -> {
            int at = index - list.getHeaderViewsCount();
            if (at >= 0 && at < tracks.size()) startPlaying(tracks, at);
        });
        enableBulkSelection(layout, list, tracks, historyAdapter);
        layout.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    /** Selection is fully owned by the screen, not ListView's native action-mode gesture detector.
     * This prevents Android's ACTION_UP from undoing a long-press selection. */
    private TrackSelection enableBulkSelection(LinearLayout layout, ListView list, List<Track> tracks, TrackAdapter adapter) {
        TrackSelection controller = new TrackSelection(tracks, adapter);
        adapter.selection = controller;
        // Page headings and normal playback shortcuts are redundant while a selection is active.
        // Album, artist and playlist detail pages keep their Back heading accessible.
        if (screen.equals("Titres") || screen.equals("Écoutés") || detailAlbum == null && detailArtist == null && detailPlaylist == null) {
            for (int i = 0; i < layout.getChildCount(); i++) {
                View child = layout.getChildAt(i);
                if (child != list) controller.compactViews.add(child);
            }
        } else {
            // Detail screens retain the heading with its Back button; hide extra action chips.
            for (int i = 1; i < layout.getChildCount(); i++) {
                View child = layout.getChildAt(i);
                if (child != list) controller.compactViews.add(child);
            }
        }
        layout.addView(controller.bar);
        list.setChoiceMode(ListView.CHOICE_MODE_NONE);
        return controller;
    }

    private final class TrackSelection {
        final Set<Integer> selected = new HashSet<>();
        final List<Track> tracks;
        final TrackAdapter adapter;
        final LinearLayout bar;
        final List<View> compactViews = new ArrayList<>();
        final TextView counter, allButton;
        boolean active;
        TrackSelection(List<Track> tracks, TrackAdapter adapter) {
            this.tracks = tracks;
            this.adapter = adapter;
            bar = Ui.vertical(MainActivity.this);
            bar.setBackground(Ui.gradient(0xFF1E302C, 0xFF1A242E, 15, MainActivity.this));
            bar.setPadding(d(12), d(11), d(12), d(11));
            LinearLayout top = Ui.horizontal(MainActivity.this);
            TextView icon = Ui.icon(MainActivity.this, "✓", 30, 20, Ui.ACCENT, Color.TRANSPARENT);
            icon.setClickable(false);
            top.addView(icon);
            counter = Ui.text(MainActivity.this, "0 sélectionné", 14, Ui.TEXT, true);
            counter.setPadding(d(6), 0, 0, 0);
            top.addView(counter, new LinearLayout.LayoutParams(0, -2, 1f));
            allButton = textButton("Tout sélectionner", Ui.ACCENT);
            allButton.setTextSize(12);
            top.addView(allButton, Ui.lp(MainActivity.this, -2, 38));
            allButton.setOnClickListener(v -> {
                if (selected.size() == tracks.size()) selected.clear();
                else for (int i = 0; i < tracks.size(); i++) selected.add(i);
                refresh();
            });
            TextView close = Ui.icon(MainActivity.this, "✕", 35, 20, Ui.MUTED, Ui.SURFACE);
            close.setContentDescription("Terminer la sélection");
            LinearLayout.LayoutParams cp = Ui.lp(MainActivity.this, 35, 35);
            cp.leftMargin = d(8);
            top.addView(close, cp);
            close.setOnClickListener(v -> finish());
            bar.addView(top);
            bar.addView(Ui.gap(MainActivity.this, 9));
            HorizontalScrollView scroll = new HorizontalScrollView(MainActivity.this);
            scroll.setHorizontalScrollBarEnabled(false);
            LinearLayout actions = Ui.horizontal(MainActivity.this);
            selectionAction(actions, "▶", "Lire", () -> apply(1));
            selectionAction(actions, "⏭", "Lire ensuite", () -> apply(2));
            selectionAction(actions, "☷", "À la file", () -> apply(3));
            selectionAction(actions, "＋", "Playlist", () -> apply(4));
            selectionAction(actions, "♡", "Favoris", () -> apply(5));
            scroll.addView(actions);
            bar.addView(scroll);
            LinearLayout.LayoutParams bp = Ui.lp(MainActivity.this, -1, -2);
            bp.setMargins(d(14), d(2), d(14), d(9));
            bar.setLayoutParams(bp);
            bar.setVisibility(View.GONE);
        }
        private void selectionAction(LinearLayout parent, String icon, String label, Runnable action) {
            LinearLayout button = Ui.horizontal(MainActivity.this);
            button.setPadding(d(9), d(8), d(12), d(8));
            Ui.clickable(button, Ui.shape(Ui.SURFACE_ALT, 11, MainActivity.this));
            TextView pict = Ui.icon(MainActivity.this, icon, 24, 17, Ui.ACCENT, Color.TRANSPARENT);
            pict.setClickable(false);
            button.addView(pict, Ui.lp(MainActivity.this, 24, 27));
            TextView name = Ui.text(MainActivity.this, label, 12, Ui.TEXT, true);
            name.setPadding(d(5), 0, 0, 0);
            button.addView(name);
            LinearLayout.LayoutParams ap = Ui.lp(MainActivity.this, -2, -2);
            ap.rightMargin = d(7);
            parent.addView(button, ap);
            button.setOnClickListener(v -> action.run());
        }
        void begin() {
            active = true;
            selected.clear();
            refresh();
        }
        void start(int index) {
            if (index < 0 || index >= tracks.size()) return;
            if (!active) { active = true; selected.clear(); }
            selected.add(index); // Never toggle a long press on finger release.
            refresh();
        }
        void toggle(int index) {
            if (!active || index < 0 || index >= tracks.size()) return;
            if (!selected.add(index)) selected.remove(index);
            if (selected.isEmpty()) finish();
            else refresh();
        }
        boolean allSelected() { return active && !tracks.isEmpty() && selected.size() == tracks.size(); }
        void refresh() {
            bar.setVisibility(active ? View.VISIBLE : View.GONE);
            setSelectionHeaderCompact(active);
            for (View chrome : compactViews) chrome.setVisibility(active ? View.GONE : View.VISIBLE);
            counter.setText(selected.size() + (selected.size() > 1 ? " titres sélectionnés" : " titre sélectionné"));
            allButton.setText(allSelected() ? "Tout désélectionner" : "Tout sélectionner");
            adapter.notifyDataSetChanged();
        }
        void finish() {
            selected.clear(); active = false;
            refresh();
        }
        private void apply(int action) {
            if (selected.isEmpty()) return;
            List<Integer> indices = new ArrayList<>(selected);
            Collections.sort(indices);
            List<Track> chosen = new ArrayList<>();
            for (int i : indices) if (i >= 0 && i < tracks.size()) chosen.add(tracks.get(i));
            if (chosen.isEmpty()) { finish(); return; }
            switch (action) {
                case 1: startPlaying(chosen, 0); break;
                case 2:
                    if (service == null) { toast("Lecteur indisponible"); return; }
                    if (service.getCurrent() == null) {
                        startPlaying(Collections.singletonList(chosen.get(0)), 0);
                        chosen.remove(0);
                    }
                    for (int i = chosen.size() - 1; i >= 0; i--) service.playNext(chosen.get(i));
                    toast("Ajouté à la suite");
                    break;
                case 3:
                    if (service == null) { toast("Lecteur indisponible"); return; }
                    if (service.getCurrent() == null) {
                        startPlaying(Collections.singletonList(chosen.get(0)), 0);
                        chosen.remove(0);
                    }
                    for (Track t : chosen) service.enqueue(t);
                    toast("Ajouté à la file");
                    break;
                case 4: addManyToPlaylist(chosen); break;
                case 5:
                    for (Track t : chosen) store.setFavorite(t.id, true);
                    toast(chosen.size() + " titres ajoutés aux favoris");
                    refreshPlayer(); break;
            }
            finish();
            if (screen.equals("Playlists")) render();
        }
    }

    private void addManyToPlaylist(List<Track> tracks) {
        List<LibraryStore.Playlist> playlists = store.playlists();
        List<OptionSheet.Item> actions = OptionSheet.items();
        actions.add(new OptionSheet.Item("＋", "Créer une playlist", "Enregistrer les " + tracks.size() + " morceaux sélectionnés", () -> {
            EditText name = new EditText(this);
            name.setSingleLine(true);
            name.setHint("Nom de la nouvelle playlist");
            new AlertDialog.Builder(this).setTitle("Nouvelle playlist").setView(name)
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton("Créer", (d, w) -> {
                        String value = name.getText().toString().trim();
                        if (value.isEmpty()) { toast("Nom obligatoire"); return; }
                        long id = store.createPlaylist(value);
                        if (id >= 0) {
                            List<Long> ids = new ArrayList<>();
                            for (Track t : tracks) ids.add(t.id);
                            store.addManyToPlaylist(id, ids);
                        }
                        toast("Playlist créée");
                        render();
                    }).show();
        }));
        for (LibraryStore.Playlist p : playlists) {
            actions.add(new OptionSheet.Item("♫", p.name, p.count + " morceaux", () -> {
                List<Long> ids = new ArrayList<>();
                for (Track t : tracks) ids.add(t.id);
                store.addManyToPlaylist(p.id, ids);
                toast(tracks.size() + " titres ajoutés à « " + p.name + " »");
                render();
            }));
        }
        OptionSheet.show(this, "Ajouter " + tracks.size() + " titres", "Choisis la playlist de destination", actions);
    }

    private void playShuffled(List<Track> tracks) {
        if (tracks.isEmpty()) return;
        if (service != null) service.setShuffle(true);
        startPlaying(tracks, new java.util.Random().nextInt(tracks.size()));
    }
    private void closeKeyboard() {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

    private static final class AlbumInfo {
        long id;
        String title, artist;
        Track representative;
        int count;
        AlbumInfo(Track t) {
            id = t.albumId;
            title = t.album;
            artist = t.artist;
            representative = t;
        }
    }
    private void drawAlbums() {
        LinearLayout layout = page();
        Map<Long, AlbumInfo> grouped = new LinkedHashMap<>();
        for (Track t : library) {
            if (!visibleMedia(t) || !matches(t.title, t.album, t.artist)) continue;
            AlbumInfo info = grouped.get(t.albumId);
            if (info == null) { info = new AlbumInfo(t); grouped.put(t.albumId, info); }
            info.count++;
        }
        List<AlbumInfo> albums = new ArrayList<>(grouped.values());
        albums.sort(Comparator.comparing(a -> a.title.toLowerCase(Locale.ROOT)));
        heading(layout, "Albums", albums.size() + " albums disponibles", null);
        if (albums.isEmpty()) {
            FrameLayout empty = new FrameLayout(this);
            layout.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1f));
            showEmpty(empty, "Aucun album", "Essaie une autre recherche.", null, null);
            return;
        }
        GridView grid = new GridView(this);
        grid.setNumColumns(2);
        grid.setHorizontalSpacing(d(13));
        grid.setVerticalSpacing(d(17));
        grid.setPadding(d(20), d(8), d(20), d(18));
        grid.setClipToPadding(false);
        grid.setSelector(android.R.color.transparent);
        grid.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return albums.size(); }
            @Override public Object getItem(int i) { return albums.get(i); }
            @Override public long getItemId(int i) { return albums.get(i).id; }
            @Override public View getView(int i, View convert, ViewGroup parent) {
                AlbumInfo info = albums.get(i);
                LinearLayout card = Ui.vertical(MainActivity.this);
                card.setPadding(d(8), d(8), d(8), d(11));
                card.setBackground(Ui.shape(Ui.SURFACE, 15, MainActivity.this));
                ImageView art = cover(132, 12);
                card.addView(art, Ui.lp(MainActivity.this, -1, 146));
                Artwork.bind(MainActivity.this, art, info.representative);
                TextView name = Ui.text(MainActivity.this, info.title, 14, Ui.TEXT, true);
                name.setMaxLines(1);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams nameP = Ui.lp(MainActivity.this, -1, 23);
                nameP.topMargin = d(10);
                card.addView(name, nameP);
                TextView sub = Ui.text(MainActivity.this, info.artist + " • " + info.count + " titres", 11, Ui.MUTED, false);
                sub.setSingleLine(true);
                sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(sub, Ui.lp(MainActivity.this, -1, 19));
                return card;
            }
        });
        grid.setOnItemClickListener((parent, v, pos, id) -> {
            AlbumInfo selected = albums.get(pos);
            detailAlbum = selected.id;
            detailName = selected.title;
            closeKeyboard();
            render();
        });
        layout.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void drawArtists() {
        LinearLayout layout = page();
        Map<String, List<Track>> grouped = new LinkedHashMap<>();
        for (Track t : library) {
            if (!visibleMedia(t) || !matches(t.title, t.artist, t.album)) continue;
            grouped.computeIfAbsent(t.artist, k -> new ArrayList<>()).add(t);
        }
        List<String> names = new ArrayList<>(grouped.keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        heading(layout, "Artistes", names.size() + " artistes dans votre musique", null);
        ListView list = listView();
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return names.size(); }
            @Override public Object getItem(int i) { return names.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View view, ViewGroup parent) {
                String name = names.get(i);
                LinearLayout row = Ui.horizontal(MainActivity.this);
                row.setPadding(d(8), d(9), d(8), d(9));
                TextView avatar = Ui.text(MainActivity.this, name.isEmpty() ? "♪" : name.substring(0, 1).toUpperCase(Locale.ROOT),
                        22, Ui.ACCENT, true);
                avatar.setGravity(Gravity.CENTER);
                avatar.setBackground(Ui.gradient(0xFF383448, 0xFF293D3C, 30, MainActivity.this));
                row.addView(avatar, Ui.lp(MainActivity.this, 55, 55));
                LinearLayout info = Ui.vertical(MainActivity.this);
                info.setPadding(d(15), 0, 0, 0);
                info.addView(Ui.text(MainActivity.this, name, 15, Ui.TEXT, true));
                info.addView(Ui.gap(MainActivity.this, 5));
                info.addView(Ui.text(MainActivity.this, grouped.get(name).size() + " morceaux", 12, Ui.MUTED, false));
                row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
                TextView chevron = Ui.text(MainActivity.this, "›", 27, Ui.MUTED, false);
                row.addView(chevron, Ui.lp(MainActivity.this, 25, 35));
                return row;
            }
        });
        list.setOnItemClickListener((p, v, pos, id) -> {
            detailArtist = names.get(pos);
            detailName = detailArtist;
            closeKeyboard(); render();
        });
        layout.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private ListView listView() {
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setSelector(android.R.color.transparent);
        list.setClipToPadding(false);
        list.setPadding(d(12), 0, d(12), d(15));
        return list;
    }
    private List<Track> playlistTracks(long id) {
        if (id == FAVORITES) {
            Set<Long> favorites = store.favoriteIds();
            List<Track> tracks = new ArrayList<>();
            for (Track t : library) if (favorites.contains(t.id)) tracks.add(t);
            return tracks;
        }
        return resolveIds(store.playlistIds(id));
    }

    private void drawPlaylists() {
        LinearLayout layout = page();
        heading(layout, "Vos playlists", "Vos morceaux préférés, organisés à votre façon", null);
        LinearLayout playlistButtons = Ui.horizontal(this);
        playlistButtons.setPadding(d(20), 0, d(20), d(12));
        LinearLayout create = actionChip("＋", "Créer une playlist", true);
        create.setGravity(Gravity.CENTER);
        playlistButtons.addView(create, new LinearLayout.LayoutParams(0, d(47), 1f));
        create.setOnClickListener(v -> askCreatePlaylist(null));
        LinearLayout importer = actionChip("⇩", "Importer", false);
        importer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, d(47), 0.7f);
        ip.leftMargin = d(10);
        playlistButtons.addView(importer, ip);
        importer.setOnClickListener(v -> importPlaylistPicker());
        layout.addView(playlistButtons);
        List<LibraryStore.Playlist> own = store.playlists();
        List<Long> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<String> counts = new ArrayList<>();
        ids.add(FAVORITES);
        labels.add("Coups de cœur  ♥");
        counts.add(store.favoriteIds().size() + " titres favoris");
        for (LibraryStore.Playlist item : own) {
            if (!matches(item.name)) continue;
            ids.add(item.id); labels.add(item.name); counts.add(item.count + " morceaux");
        }
        ListView list = listView();
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return ids.size(); }
            @Override public Object getItem(int i) { return labels.get(i); }
            @Override public long getItemId(int i) { return ids.get(i); }
            @Override public View getView(int i, View convert, ViewGroup parent) {
                boolean favorite = ids.get(i) == FAVORITES;
                LinearLayout row = Ui.horizontal(MainActivity.this);
                row.setPadding(d(9), d(10), d(9), d(10));
                TextView emblem = Ui.text(MainActivity.this, favorite ? "♥" : "♫", 27,
                        favorite ? Ui.BG : Ui.PURPLE, true);
                emblem.setGravity(Gravity.CENTER);
                emblem.setBackground(Ui.gradient(favorite ? Ui.ACCENT : 0xFF323150,
                        favorite ? 0xFF91CF56 : 0xFF303C4D, 13, MainActivity.this));
                row.addView(emblem, Ui.lp(MainActivity.this, 63, 63));
                LinearLayout names = Ui.vertical(MainActivity.this);
                names.setPadding(d(15), 0, 0, 0);
                names.addView(Ui.text(MainActivity.this, labels.get(i), 15, Ui.TEXT, true));
                names.addView(Ui.gap(MainActivity.this, 6));
                names.addView(Ui.text(MainActivity.this, counts.get(i), 12, Ui.MUTED, false));
                row.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
                TextView ellipsis = Ui.icon(MainActivity.this, favorite ? "›" : "⋮", 38, 22, Ui.MUTED, Color.TRANSPARENT);
                row.addView(ellipsis);
                if (!favorite) ellipsis.setOnClickListener(v -> playlistActions(ids.get(i), labels.get(i)));
                return row;
            }
        });
        list.setOnItemClickListener((p, v, pos, id) -> {
            detailPlaylist = ids.get(pos);
            detailName = labels.get(pos);
            closeKeyboard(); render();
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            if (ids.get(pos) != FAVORITES) playlistActions(ids.get(pos), labels.get(pos));
            else exportPlaylistPicker(FAVORITES, "Coups de cœur");
            return true;
        });
        layout.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
    }
    private void drawPlaylistDetail() {
        long playlistId = detailPlaylist;
        List<Track> all = playlistTracks(playlistId);
        List<Track> filtered = filterPreservingOrder(all);
        LinearLayout layout = page();
        heading(layout, detailName, all.size() + " titres • playlist", this::clearAndRender);
        toolbar(layout, all);
        if (filtered.isEmpty()) {
            FrameLayout empty = new FrameLayout(this);
            layout.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1f));
            showEmpty(empty, "Cette playlist est vide", "Sur un morceau, touche ⋮ puis « Ajouter à une playlist ».", null, null);
            return;
        }
        ListView list = listView();
        TrackAdapter playlistAdapter = new TrackAdapter(filtered, false);
        // Regular playlists support dragging tracks; favorites are a computed collection.
        if (playlistId != FAVORITES) {
            playlistAdapter.dragPlaylistId = playlistId;
            playlistAdapter.dragList = list;
        }
        list.setAdapter(playlistAdapter);
        activeTrackList = list;
        list.setOnItemClickListener((p, v, position, id) -> startPlaying(filtered, position));
        TrackSelection controller = enableBulkSelection(layout, list, filtered, playlistAdapter);
        if (playlistId != FAVORITES) {
            TextView selectMode = Ui.icon(this, "✓", 42, 22, Ui.TEXT, Ui.SURFACE);
            selectMode.setContentDescription("Sélectionner plusieurs morceaux");
            LinearLayout.LayoutParams sp = Ui.lp(this, 42, 42); sp.leftMargin = d(11);
            ((LinearLayout) layout.getChildAt(1)).addView(selectMode, sp);
            selectMode.setOnClickListener(v -> controller.begin());
            // Existing swipe-to-queue gestures remain available until a long press starts drag.
        }
        layout.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void drawQueue() {
        LinearLayout layout = page();
        List<Track> waiting = service == null ? Collections.emptyList() : service.upcoming();
        heading(layout, "À suivre", waiting.size() + " morceaux dans la file d'attente", null);
        if (service != null && service.getCurrent() != null) {
            LinearLayout banner = Ui.vertical(this);
            banner.setPadding(d(18), d(10), d(18), d(12));
            TextView now = Ui.text(this, "EN COURS", 11, Ui.ACCENT, true);
            banner.addView(now);
            banner.addView(Ui.gap(this, 7));
            Track current = service.getCurrent();
            TextView title = Ui.text(this, "♫  " + current.title + " — " + current.artist, 14, Ui.TEXT, true);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            banner.addView(title);
            layout.addView(banner);
        }
        if (waiting.isEmpty()) {
            FrameLayout empty = new FrameLayout(this);
            layout.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1f));
            showEmpty(empty, "La file est vide", "Depuis un morceau, choisis « Lire ensuite » ou « Ajouter à la file ».", null, null);
            return;
        }
        TextView hint = Ui.text(this, "Maintiens un titre pour le déplacer • ⋮ pour plus d’options", 11, Ui.MUTED, false);
        hint.setPadding(d(20), 0, d(10), d(9));
        layout.addView(hint);
        ListView list = listView();
        list.setAdapter(new TrackAdapter(waiting, true));
        list.setOnItemClickListener((p, v, position, id) -> playQueuedTrack(position));
        layout.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void playlistActions(long id, String name) {
        List<OptionSheet.Item> actions = OptionSheet.items();
        actions.add(new OptionSheet.Item("✎", "Renommer", "Modifier le nom de cette playlist", () -> renamePlaylist(id, name)));
        actions.add(new OptionSheet.Item("⇩", "Exporter la playlist", "Sauvegarder au format JSON", () -> exportPlaylistPicker(id, name)));
        actions.add(new OptionSheet.Item("✕", "Supprimer la playlist", "Les morceaux restent sur le téléphone", () ->
                new AlertDialog.Builder(this).setTitle("Supprimer « " + name + " » ?")
                        .setMessage("La playlist sera supprimée, mais vos fichiers musicaux seront conservés.")
                        .setNegativeButton("Annuler", null).setPositiveButton("Supprimer", (d,w) -> {
                            store.deletePlaylist(id); clearDetail(); render();
                        }).show(), true));
        OptionSheet.show(this, name, "Gérer votre sélection", actions);
    }

    private void importPlaylistPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try { startActivityForResult(intent, IMPORT_PLAYLIST); }
        catch (Exception e) { toast("Aucun gestionnaire de fichiers disponible"); }
    }

    private void exportPlaylistPicker(long id, String name) {
        exportPlaylistId = id;
        exportPlaylistName = name;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        String filename = name.replaceAll("[^a-zA-Z0-9À-ÿ _-]", "_").trim();
        intent.putExtra(Intent.EXTRA_TITLE, (filename.isEmpty() ? "playlist" : filename) + ".json");
        try { startActivityForResult(intent, EXPORT_PLAYLIST); }
        catch (Exception e) { toast("Aucun gestionnaire de fichiers disponible"); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DELETE_MEDIA_REQUEST) {
            if (resultCode == RESULT_OK) {
                if (Build.VERSION.SDK_INT == 29) requestSystemMediaDeletion();
                else completePermanentDelete();
            } else { pendingDelete = null; toast("Suppression annulée"); }
            return;
        }
        if (requestCode == PICK_WALLPAPER_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri picture = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(picture, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    getSharedPreferences("onde_settings", MODE_PRIVATE).edit()
                            .putString("theme_" + pendingThemeImageTarget + "_image", picture.toString())
                            .putString("theme_" + pendingThemeImageTarget + "_mode", "image").apply();
                    applyThemeLive();
                    if (themeEditor != null) themeEditor.refreshState();
                } catch (Exception e) { toast("Impossible de conserver cette image. Choisis une autre photo."); }
            }
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == EXPORT_PLAYLIST) {
            final long id = exportPlaylistId;
            final String name = exportPlaylistName;
            io.execute(() -> {
                try {
                    JSONObject document = new JSONObject();
                    document.put("format", "onde-playlist");
                    document.put("version", 1);
                    document.put("name", name);
                    JSONArray entries = new JSONArray();
                    List<Track> tracks;
                    if (id == FAVORITES) {
                        Set<Long> favoriteIds = store.favoriteIds();
                        tracks = new ArrayList<>();
                        for (Track t : MusicRepository.all()) if (favoriteIds.contains(t.id)) tracks.add(t);
                    } else tracks = resolveIds(store.playlistIds(id));
                    for (Track track : tracks) {
                        JSONObject item = new JSONObject();
                        item.put("id", track.id);
                        item.put("title", track.title);
                        item.put("artist", track.artist);
                        item.put("album", track.album);
                        item.put("isVideo", track.isVideo);
                        entries.put(item);
                    }
                    document.put("tracks", entries);
                    try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                        if (stream == null) throw new IllegalStateException("Document inaccessible");
                        stream.write(document.toString(2).getBytes(StandardCharsets.UTF_8));
                    }
                    runOnUiThread(() -> toast("Playlist exportée : " + entries.length() + " titres"));
                } catch (Exception e) { runOnUiThread(() -> toast("Export impossible : " + e.getMessage())); }
            });
        } else if (requestCode == IMPORT_PLAYLIST) {
            io.execute(() -> {
                try {
                    byte[] raw;
                    try (InputStream input = getContentResolver().openInputStream(uri);
                         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        if (input == null) throw new IllegalArgumentException("Fichier illisible");
                        byte[] buffer = new byte[8192];
                        int n, size = 0;
                        while ((n = input.read(buffer)) != -1) {
                            size += n;
                            if (size > 3_000_000) throw new IllegalArgumentException("Fichier trop volumineux (3 Mo maximum)");
                            out.write(buffer, 0, n);
                        }
                        raw = out.toByteArray();
                    }
                    JSONObject doc = new JSONObject(new String(raw, StandardCharsets.UTF_8));
                    if (!"onde-playlist".equals(doc.optString("format")))
                        throw new IllegalArgumentException("Format non reconnu : importer un export Onde (.json)");
                    String name = doc.optString("name", "Playlist importée").trim();
                    if (name.isEmpty()) name = "Playlist importée";
                    final String finalName = name + " (importée)";
                    JSONArray entries = doc.getJSONArray("tracks");
                    if (entries.length() > 5000) throw new IllegalArgumentException("Trop de morceaux");
                    List<Long> ids = new ArrayList<>();
                    Set<Long> unique = new HashSet<>();
                    Map<String, Long> byMetadata = new HashMap<>();
                    for (Track t : MusicRepository.all()) {
                        String key = t.title.toLowerCase(Locale.ROOT) + "|" + t.artist.toLowerCase(Locale.ROOT)
                                + "|" + t.album.toLowerCase(Locale.ROOT) + "|" + t.isVideo;
                        byMetadata.put(key, t.id);
                    }
                    for (int i = 0; i < entries.length(); i++) {
                        JSONObject entry = entries.optJSONObject(i);
                        if (entry == null) continue;
                        long id = entry.optLong("id", -1);
                        Track match = MusicRepository.get(id);
                        if (match == null || !match.title.equalsIgnoreCase(entry.optString("title"))
                                || !match.artist.equalsIgnoreCase(entry.optString("artist"))
                                || !match.album.equalsIgnoreCase(entry.optString("album"))
                                || match.isVideo != entry.optBoolean("isVideo", false)) {
                            String key = entry.optString("title").toLowerCase(Locale.ROOT) + "|"
                                    + entry.optString("artist").toLowerCase(Locale.ROOT) + "|"
                                    + entry.optString("album").toLowerCase(Locale.ROOT)
                                    + "|" + entry.optBoolean("isVideo", false);
                            Long found = byMetadata.get(key);
                            id = found == null ? -1 : found;
                        }
                        if (id != -1 && unique.add(id)) ids.add(id);
                    }
                    final int total = entries.length();
                    runOnUiThread(() -> {
                        long playlistId = store.createPlaylist(finalName);
                        if (playlistId < 0) { toast("Création impossible"); return; }
                        store.addManyToPlaylist(playlistId, ids);
                        toast("Importé : " + ids.size() + " titres retrouvés sur " + total);
                        screen = "Playlists";
                        detailPlaylist = playlistId;
                        detailName = finalName;
                        rebuildTabs(); render();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Import impossible")
                            .setMessage(e.getMessage()).setPositiveButton("OK", null).show());
                }
            });
        }
    }

    private void renamePlaylist(long id, String previous) {
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setText(previous);
        name.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Renommer la playlist").setView(name)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    String value = name.getText().toString().trim();
                    if (value.isEmpty()) { toast("Nom vide"); return; }
                    store.rename(id, value);
                    if (detailPlaylist != null && detailPlaylist == id) detailName = value;
                    render();
                }).show();
    }
    private void askCreatePlaylist(Track append) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Ex. : Trajet de nuit");
        new AlertDialog.Builder(this).setTitle("Nouvelle playlist").setView(input)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Créer", (d, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) { toast("Donne un nom à la playlist"); return; }
                    long id = store.createPlaylist(name);
                    if (append != null && id != -1) store.addToPlaylist(id, append.id);
                    toast("Playlist « " + name + " » créée");
                    render();
                }).show();
    }
    /** Persistent checkbox sheet: checked = already included; uncheck to remove. */
    private void addToPlaylist(Track track) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout sheet = Ui.vertical(this);
        sheet.setPadding(d(20), d(9), d(20), d(23));
        sheet.setBackground(Ui.gradient(Ui.SURFACE_ALT, Ui.SURFACE, 25, this));
        LinearLayout gripArea = Ui.vertical(this);
        gripArea.setGravity(Gravity.CENTER);
        View grip = new View(this);
        grip.setBackground(Ui.shape(Ui.MUTED, 6, this));
        gripArea.addView(grip, Ui.lp(this, 42, 4));
        gripArea.setContentDescription("Glisser vers le bas pour réduire ou fermer le volet");
        sheet.addView(gripArea, Ui.lp(this, -1, 37));
        TextView title = Ui.text(this, "Dans quelles playlists ?", 21, Ui.TEXT, true);
        sheet.addView(title);
        TextView subtitle = Ui.text(this, track.title, 12, Ui.MUTED, false);
        subtitle.setMaxLines(2);
        subtitle.setPadding(0, d(5), 0, d(14));
        sheet.addView(subtitle);
        ScrollView scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        LinearLayout rows = Ui.vertical(this);
        boolean[] changed = {false};
        List<LibraryStore.Playlist> playlists = store.playlists();
        for (LibraryStore.Playlist playlist : playlists) {
            LinearLayout row = Ui.horizontal(this);
            row.setPadding(d(12), d(10), d(10), d(10));
            Ui.clickable(row, Ui.shape(Ui.SURFACE_ALT, 13, this));
            LinearLayout names = Ui.vertical(this);
            names.addView(Ui.text(this, playlist.name, 14, Ui.TEXT, true));
            names.addView(Ui.gap(this, 4));
            names.addView(Ui.text(this, playlist.count + " morceaux", 11, Ui.MUTED, false));
            row.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
            CheckBox check = new CheckBox(this);
            check.setButtonTintList(ColorStateList.valueOf(Ui.ACCENT));
            check.setChecked(store.playlistContains(playlist.id, track.id));
            row.addView(check, Ui.lp(this, 42, 46));
            check.setOnCheckedChangeListener((button, isChecked) -> {
                if (isChecked) store.addToPlaylist(playlist.id, track.id);
                else store.removeFromPlaylist(playlist.id, track.id);
                changed[0] = true;
            });
            row.setOnClickListener(v -> check.setChecked(!check.isChecked()));
            LinearLayout.LayoutParams rp = Ui.lp(this, -1, -2);
            rp.bottomMargin = d(7);
            rows.addView(row, rp);
        }
        if (playlists.isEmpty()) {
            TextView empty = Ui.text(this, "Aucune playlist pour le moment. Crée la première ci-dessous.", 13, Ui.MUTED, false);
            empty.setPadding(0, d(12), 0, d(18));
            rows.addView(empty);
        }
        scroller.addView(rows);
        sheet.addView(scroller, new LinearLayout.LayoutParams(-1,
                Math.min(d(450), d(76) * Math.max(1, playlists.size()))));
        TextView create = Ui.text(this, "＋  Créer une nouvelle playlist", 14, Ui.ACCENT, true);
        create.setGravity(Gravity.CENTER);
        create.setPadding(0, d(16), 0, d(16));
        sheet.addView(create);
        create.setOnClickListener(v -> { dialog.dismiss(); askCreatePlaylist(track); });
        TextView done = Ui.text(this, "Terminé", 14, Ui.BG, true);
        done.setGravity(Gravity.CENTER);
        done.setBackground(Ui.shape(Ui.ACCENT, 14, this));
        sheet.addView(done, Ui.lp(this, -1, 46));
        done.setOnClickListener(v -> dialog.dismiss());
        // Keep the same downward collapse / dismiss gesture as other bottom drawers.
        final float[] touchDown = {0};
        final boolean[] collapsed = {false};
        View.OnTouchListener drag = (v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchDown[0] = event.getRawY(); return true;
                case MotionEvent.ACTION_MOVE:
                    sheet.setTranslationY(Math.max(0, event.getRawY() - touchDown[0]) * .45f);
                    return true;
                case MotionEvent.ACTION_UP:
                    float delta = event.getRawY() - touchDown[0];
                    sheet.animate().translationY(0).setDuration(160).start();
                    if (delta > d(210) || (collapsed[0] && delta > d(75))) dialog.dismiss();
                    else if (delta > d(75)) {
                        collapsed[0] = true;
                        scroller.setVisibility(View.GONE);
                        create.setVisibility(View.GONE);
                        done.setVisibility(View.GONE);
                    } else if (delta < -d(60) && collapsed[0]) {
                        collapsed[0] = false;
                        scroller.setVisibility(View.VISIBLE);
                        create.setVisibility(View.VISIBLE);
                        done.setVisibility(View.VISIBLE);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    sheet.animate().translationY(0).setDuration(130).start();
                    return true;
                default: return false;
            }
        };
        gripArea.setOnTouchListener(drag);
        title.setOnTouchListener(drag);
        dialog.setContentView(sheet);
        android.view.Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            win.setGravity(Gravity.BOTTOM);
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            win.setDimAmount(.62f);
        }
        dialog.setOnDismissListener(v -> { if (changed[0]) render(); });
        dialog.show();
        if (win != null) win.setLayout(-1, -2);
    }

    private void toggleFavorite(Track track) {
        boolean newValue = !store.isFavorite(track.id);
        store.setFavorite(track.id, newValue);
        toast(newValue ? "Ajouté aux favoris ♥" : "Retiré des favoris");
        if (screen.equals("Playlists")) render();
        refreshPlayer();
    }
    private void trackActions(Track track) {
        boolean favorite = store.isFavorite(track.id);
        boolean canRemove = detailPlaylist != null && detailPlaylist != FAVORITES && screen.equals("Playlists");
        List<OptionSheet.Item> actions = OptionSheet.items();
        actions.add(new OptionSheet.Item("▶", "Lire maintenant", track.isVideo ? "Ouvrir le lecteur vidéo" : "Démarrer ce morceau", () -> {
            startPlaying(Collections.singletonList(track), 0);
            if (track.isVideo) root.postDelayed(this::showFullPlayer, 240);
        }));
        if (track.isVideo) {
            actions.add(new OptionSheet.Item("⛶", "Plein écran vidéo", "Lire avec l'image en mode paysage", () -> {
                startPlaying(Collections.singletonList(track), 0);
                root.postDelayed(() -> { showFullPlayer(); enterVideoFullscreen(); }, 290);
            }));
        }
        actions.add(new OptionSheet.Item("⏭", "Lire juste après", "Prochain morceau dans la file", () -> playImmediatelyAfterCurrent(track)));
        actions.add(new OptionSheet.Item("☷", "Ajouter à la fin de la file", "Conserver les morceaux à suivre", () -> appendToQueue(track)));
        actions.add(new OptionSheet.Item("＋", "Ajouter à une playlist", "Enregistrer dans une sélection personnelle", () -> addToPlaylist(track)));
        actions.add(new OptionSheet.Item(favorite ? "♡" : "♥", favorite ? "Retirer des favoris" : "Ajouter aux favoris",
                favorite ? "Retirer le cœur de ce morceau" : "Retrouver facilement ce titre", () -> toggleFavorite(track)));
        if (canRemove) {
            actions.add(new OptionSheet.Item("↑", "Monter dans la playlist", "Déplacer d'une position", () -> {
                store.moveInPlaylist(detailPlaylist, track.id, -1); render();
            }));
            actions.add(new OptionSheet.Item("↓", "Descendre dans la playlist", "Déplacer d'une position", () -> {
                store.moveInPlaylist(detailPlaylist, track.id, 1); render();
            }));
            actions.add(new OptionSheet.Item("✕", "Retirer de cette playlist", "Le fichier musical reste sur le téléphone", () -> {
                store.removeFromPlaylist(detailPlaylist, track.id); render();
            }, true));
        }
        actions.add(new OptionSheet.Item("✕", "Supprimer définitivement du téléphone",
                "Efface le fichier audio/vidéo de l'appareil, après confirmation", () -> confirmPermanentDelete(track), true));
        OptionSheet.show(this, track.title, track.artist + "  ·  " + Track.time(track.durationMs), actions);
    }
    private void confirmPermanentDelete(Track track) {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer définitivement ?")
                .setMessage("« " + track.title + " » sera effacé du stockage de ce téléphone. Il disparaîtra aussi de toutes les playlists et des favoris.\n\nCette opération est irréversible et ne correspond pas à un simple retrait de playlist. Android peut demander une seconde confirmation.")
                .setNegativeButton("Conserver", null)
                .setPositiveButton("Supprimer le fichier", (dialog, which) -> {
                    pendingDelete = track;
                    requestSystemMediaDeletion();
                }).show();
    }

    private void requestSystemMediaDeletion() {
        final Track item = pendingDelete;
        if (item == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                PendingIntent request = MediaStore.createDeleteRequest(getContentResolver(),
                        Collections.singletonList(item.uri));
                startIntentSenderForResult(request.getIntentSender(), DELETE_MEDIA_REQUEST,
                        null, 0, 0, 0);
                return;
            }
            if (Build.VERSION.SDK_INT <= 28 &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, LEGACY_WRITE_REQUEST);
                return;
            }
            if (getContentResolver().delete(item.uri, null, null) > 0) completePermanentDelete();
            else { pendingDelete = null; toast("Fichier introuvable ou non supprimé"); }
        } catch (SecurityException error) {
            if (Build.VERSION.SDK_INT == 29 && error instanceof RecoverableSecurityException) {
                try {
                    PendingIntent permission = ((RecoverableSecurityException)error).getUserAction().getActionIntent();
                    startIntentSenderForResult(permission.getIntentSender(), DELETE_MEDIA_REQUEST,
                            null, 0, 0, 0);
                } catch (Exception e) { pendingDelete = null; toast("Autorisation de suppression impossible"); }
            } else { pendingDelete = null; toast("Android refuse la suppression de ce fichier"); }
        } catch (Exception error) {
            pendingDelete = null;
            toast("Échec de la suppression : " + error.getMessage());
        }
    }

    private void completePermanentDelete() {
        Track item = pendingDelete;
        pendingDelete = null;
        if (item == null) return;
        if (service != null) {
            if (service.getCurrent() != null && service.getCurrent().id == item.id)
                service.stopPlayback();
            else service.forgetDeletedTrack(item.id);
        }
        store.forgetDeletedTrack(item.id);
        toast("Fichier supprimé : " + item.title);
        if (hasAudioPermission() || hasVideoPermission()) scanLibrary();
        else render();
    }

    private void queueActions(int at, Track track) {
        if (service == null) return;
        List<OptionSheet.Item> actions = OptionSheet.items();
        actions.add(new OptionSheet.Item("↑", "Monter", "Déplacer vers le début de la file", () -> { service.reorder(at, at-1); render(); }));
        actions.add(new OptionSheet.Item("↓", "Descendre", "Déplacer vers la fin de la file", () -> { service.reorder(at, at+1); render(); }));
        actions.add(new OptionSheet.Item("⏭", "Passer en premier", "Jouer dès la fin du titre actuel", () -> { service.reorder(at, 0); render(); }));
        actions.add(new OptionSheet.Item("✕", "Retirer de la file", "Conserver le morceau dans la bibliothèque", () -> {
            service.removeUpcoming(at); render();
        }, true));
        OptionSheet.show(this, track.title, "Options de file d'attente", actions);
    }

    private final class TrackAdapter extends BaseAdapter {
        final List<Track> tracks;
        final boolean queueMode;
        final boolean showCounts;
        TrackSelection selection;
        Long dragPlaylistId;
        ListView dragList;
        private long lastDragScrollTime;
        TrackAdapter(List<Track> tracks, boolean queueMode) { this(tracks, queueMode, false); }
        TrackAdapter(List<Track> tracks, boolean queueMode, boolean showCounts) {
            this.tracks = tracks; this.queueMode = queueMode; this.showCounts = showCounts;
        }
        @Override public int getCount() { return tracks.size(); }
        @Override public Object getItem(int position) { return tracks.get(position); }
        @Override public long getItemId(int pos) { return tracks.get(pos).id; }
        final class Holder {
            SwipeTrackRow root;
            CheckBox check;
            ImageView art;
            TextView title, sub, member, more, dragGrip;
        }
        @Override public View getView(int position, View convert, ViewGroup parent) {
            Holder holder;
            if (convert == null) {
                SwipeTrackRow row = new SwipeTrackRow(MainActivity.this);
                LinearLayout front = row.content();
                CheckBox check = new CheckBox(MainActivity.this);
                check.setButtonTintList(ColorStateList.valueOf(Ui.ACCENT));
                check.setClickable(false);
                check.setFocusable(false);
                check.setFocusableInTouchMode(false);
                check.setContentDescription("Cocher ce morceau");
                check.setVisibility(View.GONE);
                LinearLayout.LayoutParams checkParams = Ui.lp(MainActivity.this, 40, 44);
                checkParams.rightMargin = d(5);
                front.addView(check, checkParams);
                ImageView art = cover(53, 9);
                front.addView(art, Ui.lp(MainActivity.this, 53, 53));
                LinearLayout labels = Ui.vertical(MainActivity.this);
                labels.setPadding(d(13), 0, d(4), 0);
                TextView title = Ui.text(MainActivity.this, "", 14, Ui.TEXT, true);
                title.setSingleLine(true);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                TextView sub = Ui.text(MainActivity.this, "", 11, Ui.MUTED, false);
                sub.setSingleLine(true);
                sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
                labels.addView(title);
                labels.addView(Ui.gap(MainActivity.this, 5));
                labels.addView(sub);
                front.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
                TextView member = Ui.text(MainActivity.this, "", 10, Ui.ACCENT, true);
                member.setGravity(Gravity.CENTER);
                member.setPadding(d(7), d(5), d(7), d(5));
                member.setBackground(Ui.shape(Ui.SURFACE_ALT, 9, MainActivity.this));
                member.setVisibility(View.GONE);
                LinearLayout.LayoutParams mb = Ui.lp(MainActivity.this, -2, -2);
                mb.rightMargin = d(3);
                front.addView(member, mb);
                TextView dragGrip = Ui.icon(MainActivity.this, "☷", 28, 20, Ui.MUTED, Color.TRANSPARENT);
                dragGrip.setVisibility(View.GONE);
                dragGrip.setClickable(false);
                dragGrip.setContentDescription("Maintenir pour déplacer le titre");
                front.addView(dragGrip, Ui.lp(MainActivity.this, 28, 43));
                TextView more = Ui.icon(MainActivity.this, "⋮", 40, 25, Ui.MUTED, Color.TRANSPARENT);
                more.setContentDescription("Options du morceau");
                more.setClickable(true);
                front.addView(more);
                holder = new Holder();
                holder.root = row; holder.check = check; holder.art = art;
                holder.title = title; holder.sub = sub; holder.member = member; holder.more = more;
                holder.dragGrip = dragGrip;
                convert = row; convert.setTag(holder);
            } else holder = (Holder) convert.getTag();
            Track t = tracks.get(position);
            holder.title.setText(t.title);
            holder.title.setTextColor(service != null && service.getCurrent() != null
                    && service.getCurrent().id == t.id ? Ui.ACCENT : Ui.TEXT);
            if (showCounts) {
                int count = store.playCount(t.id);
                holder.sub.setText((t.isVideo ? "▶ VIDÉO  •  " : "♫  ") + t.artist + "  •  " + count + (count == 1 ? " écoute" : " écoutes")
                        + "  •  " + listeningTime(store.listenedMs(t.id)));
            } else holder.sub.setText((t.isVideo ? "▶ VIDÉO  •  " : "♫  ") + t.artist + "  •  " + Track.time(t.durationMs));
            int memberCount = playlistMembershipCounts.containsKey(t.id) ? playlistMembershipCounts.get(t.id) : 0;
            holder.member.setVisibility(!queueMode && memberCount > 0 ? View.VISIBLE : View.GONE);
            holder.member.setText("✓ " + memberCount);
            holder.member.setContentDescription("Dans " + memberCount + (memberCount == 1 ? " playlist" : " playlists") + ". Toucher pour gérer.");
            holder.member.setOnClickListener(v -> addToPlaylist(t));
            Artwork.bind(MainActivity.this, holder.art, t);
            final int idx = position;
            boolean selecting = selection != null && selection.active;
            boolean selected = selecting && selection.selected.contains(idx);
            holder.check.setVisibility(selecting && !selection.allSelected() ? View.VISIBLE : View.GONE);
            holder.check.setChecked(selected);
            holder.root.resetPosition();
            holder.root.setSelectionVisual(selected);
            holder.root.setSwipeEnabled(!queueMode);
            holder.dragGrip.setVisibility(dragPlaylistId != null ? View.VISIBLE : View.GONE);
            holder.more.setOnClickListener(v -> {
                if (queueMode) queueActions(idx, t);
                else trackActions(t);
            });
            final SwipeTrackRow row = holder.root;
            if (queueMode) {
                row.configure(() -> playQueuedTrack(idx), () -> {
                    ClipData data = ClipData.newPlainText("onde_queue_position", String.valueOf(idx));
                    row.startDragAndDrop(data, new View.DragShadowBuilder(row), null, 0);
                }, null, null);
                row.setOnDragListener((v, event) -> {
                    switch (event.getAction()) {
                        case DragEvent.ACTION_DRAG_STARTED:
                            return event.getClipDescription() != null
                                    && event.getClipDescription().hasMimeType("text/plain");
                        case DragEvent.ACTION_DRAG_ENTERED:
                            row.setSelectionVisual(true); return true;
                        case DragEvent.ACTION_DRAG_EXITED:
                        case DragEvent.ACTION_DRAG_ENDED:
                            row.setSelectionVisual(false); return true;
                        case DragEvent.ACTION_DROP:
                            row.setSelectionVisual(false);
                            try {
                                if (service != null && event.getClipData() != null) {
                                    int from = Integer.parseInt(event.getClipData().getItemAt(0).getText().toString());
                                    service.reorder(from, idx);
                                    render(); return true;
                                }
                            } catch (Exception ignored) {}
                            return false;
                        default: return true;
                    }
                });
            } else {
                row.configure(() -> {
                    if (selection != null && selection.active) selection.toggle(idx);
                    else startPlaying(tracks, idx);
                }, () -> {
                    if (selection != null && selection.active) selection.start(idx);
                    else if (dragPlaylistId != null) {
                        ClipData data = ClipData.newPlainText("onde_playlist_track", String.valueOf(t.id));
                        row.startDragAndDrop(data, new View.DragShadowBuilder(row), Long.valueOf(t.id), 0);
                    } else if (selection != null) selection.start(idx);
                }, () -> playImmediatelyAfterCurrent(t), () -> appendToQueue(t));
                if (dragPlaylistId == null) row.setOnDragListener(null);
                else row.setOnDragListener((v, event) -> {
                    if (selection != null && selection.active) return false;
                    switch (event.getAction()) {
                        case DragEvent.ACTION_DRAG_STARTED:
                            return event.getLocalState() instanceof Long;
                        case DragEvent.ACTION_DRAG_ENTERED:
                            row.setSelectionVisual(true); return true;
                        case DragEvent.ACTION_DRAG_LOCATION:
                            // Scroll automatically when a dragged title reaches either edge of the list.
                            if (dragList != null && SystemClock.uptimeMillis() - lastDragScrollTime > 90) {
                                int[] rowPos = new int[2], listPos = new int[2];
                                row.getLocationOnScreen(rowPos);
                                dragList.getLocationOnScreen(listPos);
                                float listY = rowPos[1] - listPos[1] + event.getY();
                                int edge = d(52);
                                if (listY < edge) { dragList.smoothScrollBy(-d(55), 120); lastDragScrollTime = SystemClock.uptimeMillis(); }
                                else if (listY > dragList.getHeight() - edge) {
                                    dragList.smoothScrollBy(d(55), 120); lastDragScrollTime = SystemClock.uptimeMillis();
                                }
                            }
                            return true;
                        case DragEvent.ACTION_DRAG_EXITED:
                        case DragEvent.ACTION_DRAG_ENDED:
                            row.setSelectionVisual(false); return true;
                        case DragEvent.ACTION_DROP:
                            row.setSelectionVisual(false);
                            Object source = event.getLocalState();
                            if (source instanceof Long) {
                                final long movingId = (Long) source;
                                final long targetId = t.id;
                                final long playlistId = dragPlaylistId;
                                // Re-render after DragEvent finishes dispatching to its old views.
                                handler.post(() -> {
                                    if (store.movePlaylistTrack(playlistId, movingId, targetId)) {
                                        toast("Ordre de la playlist enregistré");
                                        if (screen.equals("Playlists") && detailPlaylist != null
                                                && detailPlaylist == playlistId) render();
                                    }
                                });
                                return true;
                            }
                            return false;
                        default: return true;
                    }
                });
            }
            row.setContentDescription(t.title + ", " + t.artist + ". Toucher pour lire, "
                    + (dragPlaylistId != null ? "appui long pour déplacer, " : "appui long pour sélectionner, ")
                    + "glisser horizontalement pour ajouter à la file.");
            return convert;
        }
    }

    private void buildFullPlayer() {
        ScrollView scroll = new ScrollView(this);
        fullScroll = scroll;
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        fullOverlay.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout inner = Ui.vertical(this);
        inner.setPadding(d(22), d(10), d(22), d(32));
        scroll.addView(inner);

        LinearLayout top = Ui.horizontal(this);
        TextView down = Ui.icon(this, "⌄", 43, 32, Ui.TEXT, Ui.SURFACE);
        down.setContentDescription("Réduire le lecteur");
        down.setOnClickListener(v -> hideFullPlayer());
        top.addView(down);
        TextView label = Ui.text(this, "EN COURS DE LECTURE", 11, Ui.MUTED, true);
        label.setGravity(Gravity.CENTER);
        top.addView(label, new LinearLayout.LayoutParams(0, d(45), 1f));
        TextView queue = Ui.icon(this, "☷", 43, 24, Ui.TEXT, Ui.SURFACE);
        queue.setContentDescription("Afficher la file d'attente");
        queue.setOnClickListener(v -> openQueue());
        top.addView(queue);
        TextView sound = Ui.icon(this, "≋", 43, 23, Ui.TEXT, Ui.SURFACE);
        sound.setContentDescription("Égaliseur audio");
        LinearLayout.LayoutParams soundP = Ui.lp(this, 43, 43);
        soundP.leftMargin = d(8);
        top.addView(sound, soundP);
        sound.setOnClickListener(v -> showEqualizerDialog());
        inner.addView(top);
        TextView timer = Ui.icon(this, "◷", 43, 23, Ui.TEXT, Ui.SURFACE);
        timer.setContentDescription("Minuteur de sommeil");
        timer.setOnClickListener(v -> showSleepDialog());
        LinearLayout.LayoutParams timerParams = Ui.lp(this, 43, 43);
        timerParams.leftMargin = d(8);
        top.addView(timer, timerParams);
        inner.addView(Ui.gap(this, 12));

        artworkFrame = new FrameLayout(this);
        artworkFrame.setBackground(Ui.gradient(0xFF2B3946, 0xFF4A344D, 26, this));
        artworkFrame.setClipToOutline(true);
        fullCover = cover(280, 26);
        artworkFrame.addView(fullCover, new FrameLayout.LayoutParams(-1, -1));
        buildVideoPane();
        artworkFrame.addView(videoPane, new FrameLayout.LayoutParams(-1, -1));
        videoPane.setVisibility(View.GONE);
        LinearLayout.LayoutParams artParams = Ui.lp(this, -1, 310);
        artParams.setMargins(d(6), 0, d(6), d(28));
        inner.addView(artworkFrame, artParams);

        LinearLayout titleRow = Ui.horizontal(this);
        LinearLayout names = Ui.vertical(this);
        fullTitle = Ui.text(this, "", 26, Ui.TEXT, true);
        fullTitle.setMaxLines(2);
        fullTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        names.addView(fullTitle);
        names.addView(Ui.gap(this, 7));
        fullArtist = Ui.text(this, "", 15, Ui.MUTED, false);
        fullArtist.setMaxLines(2);
        names.addView(fullArtist);
        titleRow.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        heartButton = Ui.icon(this, "♡", 48, 31, Ui.MUTED, Ui.SURFACE);
        heartButton.setContentDescription("Ajouter aux favoris");
        heartButton.setOnClickListener(v -> {
            if (service != null && service.getCurrent() != null) toggleFavorite(service.getCurrent());
        });
        LinearLayout.LayoutParams heartP = Ui.lp(this, 48, 48);
        heartP.leftMargin = d(12);
        titleRow.addView(heartButton, heartP);
        inner.addView(titleRow);
        inner.addView(Ui.gap(this, 22));

        progress = new SeekBar(this);
        progress.setMax(1000);
        progress.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        progress.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF3F4650));
        progress.setPadding(0, d(5), 0, d(5));
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) { userSeeking = true; }
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser && service != null) elapsed.setText(Track.time(service.getDuration() * value / 1000L));
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (service != null) service.seek(service.getDuration() * bar.getProgress() / 1000L);
                userSeeking = false;
            }
        });
        inner.addView(progress, Ui.lp(this, -1, 35));
        LinearLayout times = Ui.horizontal(this);
        elapsed = Ui.text(this, "0:00", 12, Ui.MUTED, false);
        total = Ui.text(this, "0:00", 12, Ui.MUTED, false);
        times.addView(elapsed, new LinearLayout.LayoutParams(0, -2, 1f));
        times.addView(total);
        inner.addView(times);
        inner.addView(Ui.gap(this, 22));

        LinearLayout controls = Ui.horizontal(this);
        controls.setGravity(Gravity.CENTER);
        shuffleButton = Ui.icon(this, "⤨", 48, 27, Ui.MUTED, Color.TRANSPARENT);
        shuffleButton.setContentDescription("Lecture aléatoire");
        shuffleButton.setOnClickListener(v -> { if (service != null) service.setShuffle(!service.isShuffle()); });
        LinearLayout.LayoutParams shuffleP = new LinearLayout.LayoutParams(0, d(52), 1f);
        shuffleP.rightMargin = d(3);
        controls.addView(shuffleButton, shuffleP);
        TextView prev = Ui.icon(this, "⏮", 52, 29, Ui.TEXT, Color.TRANSPARENT);
        prev.setContentDescription("Morceau précédent");
        prev.setOnClickListener(v -> { if (service != null) service.previous(); });
        LinearLayout.LayoutParams prevP = new LinearLayout.LayoutParams(0, d(52), 1f);
        prevP.rightMargin = d(4);
        controls.addView(prev, prevP);
        fullPause = Ui.icon(this, "▶", 72, 36, Ui.BG, Ui.ACCENT);
        fullPause.setContentDescription("Lire ou mettre en pause");
        fullPause.setOnClickListener(v -> { if (service != null) service.toggle(); });
        LinearLayout.LayoutParams playP = Ui.lp(this, 72, 72);
        playP.leftMargin = d(4);
        playP.rightMargin = d(4);
        controls.addView(fullPause, playP);
        TextView next = Ui.icon(this, "⏭", 52, 29, Ui.TEXT, Color.TRANSPARENT);
        next.setContentDescription("Morceau suivant");
        next.setOnClickListener(v -> { if (service != null) service.next(); });
        LinearLayout.LayoutParams nextP = new LinearLayout.LayoutParams(0, d(52), 1f);
        nextP.leftMargin = d(4);
        controls.addView(next, nextP);
        repeatButton = Ui.icon(this, "↻", 48, 28, Ui.MUTED, Color.TRANSPARENT);
        repeatButton.setContentDescription("Mode répétition");
        repeatButton.setOnClickListener(v -> { if (service != null) service.cycleRepeat(); });
        LinearLayout.LayoutParams repeatP = new LinearLayout.LayoutParams(0, d(52), 1f);
        repeatP.leftMargin = d(3);
        controls.addView(repeatButton, repeatP);
        inner.addView(controls);
        inner.addView(Ui.gap(this, 13));

        LinearLayout modeText = Ui.horizontal(this);
        TextView shuffleHint = Ui.text(this, "ALÉATOIRE", 10, Ui.MUTED, true);
        shuffleHint.setGravity(Gravity.CENTER);
        modeText.addView(shuffleHint, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView repeatHint = Ui.text(this, "RÉPÉTITION", 10, Ui.MUTED, true);
        repeatHint.setGravity(Gravity.CENTER);
        modeText.addView(repeatHint, new LinearLayout.LayoutParams(0, -2, 1f));
        inner.addView(modeText);
        inner.addView(Ui.gap(this, 24));

        LinearLayout shortcutRow = Ui.horizontal(this);
        TextView addPlaylist = textButton("＋  Playlist", Ui.TEXT);
        addPlaylist.setGravity(Gravity.CENTER);
        addPlaylist.setOnClickListener(v -> {
            if (service != null && service.getCurrent() != null) addToPlaylist(service.getCurrent());
        });
        shortcutRow.addView(addPlaylist, new LinearLayout.LayoutParams(0, d(49), 1f));
        TextView showQueue = textButton("☷  File d'attente", Ui.TEXT);
        showQueue.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sqParams = new LinearLayout.LayoutParams(0, d(49), 1f);
        sqParams.leftMargin = d(12);
        shortcutRow.addView(showQueue, sqParams);
        showQueue.setOnClickListener(v -> openQueue());
        inner.addView(shortcutRow);
        inner.addView(Ui.gap(this, 25));

        LinearLayout queuePreview = Ui.horizontal(this);
        queuePreview.setPadding(d(15), d(14), d(12), d(14));
        Ui.clickable(queuePreview, Ui.shape(Ui.SURFACE, 15, this));
        LinearLayout qLabels = Ui.vertical(this);
        qLabels.addView(Ui.text(this, "PROCHAINS MORCEAUX", 12, Ui.TEXT, true));
        qLabels.addView(Ui.gap(this, 5));
        fullQueueCount = Ui.text(this, "File vide", 12, Ui.MUTED, false);
        qLabels.addView(fullQueueCount);
        queuePreview.addView(qLabels, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView arrow = Ui.text(this, "→", 25, Ui.ACCENT, true);
        queuePreview.addView(arrow);
        queuePreview.setOnClickListener(v -> openQueue());
        inner.addView(queuePreview);
        buildFullscreenVideoControls();
    }

    private TextView videoControl(String glyph, String description) {
        TextView button = Ui.icon(this, glyph, 45, 23, Ui.TEXT, 0xCC253137);
        button.setContentDescription(description);
        return button;
    }

    private void attachVideoTextureIfReady() {
        if (service == null || videoSurface == null || !videoSurface.isAvailable()) return;
        if (attachedVideoSurface == null || !attachedVideoSurface.isValid()) {
            SurfaceTexture texture = videoSurface.getSurfaceTexture();
            if (texture == null) return;
            if (attachedVideoSurface != null) attachedVideoSurface.release();
            attachedVideoSurface = new Surface(texture);
        }
        service.setVideoSurface(attachedVideoSurface);
    }

    private void buildVideoPane() {
        videoPane = new FrameLayout(this);
        videoPane.setBackgroundColor(Color.BLACK);
        videoPane.setClipChildren(true);
        // TextureView stays composited with the screen, unlike SurfaceView which may remain
        // black when its parent is animated, hidden or reparented into the fullscreen overlay.
        videoSurface = new TextureView(this);
        videoSurface.setOpaque(true);
        videoPane.addView(videoSurface, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        videoSurface.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                if (attachedVideoSurface != null) attachedVideoSurface.release();
                attachedVideoSurface = new Surface(texture);
                textureFrameReady = false;
                if (service != null) service.setVideoSurface(attachedVideoSurface);
                updateVideoAspect();
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
                updateVideoAspect();
            }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                if (service != null) service.setVideoSurface(null);
                if (attachedVideoSurface != null) attachedVideoSurface.release();
                attachedVideoSurface = null;
                textureFrameReady = false;
                return true;
            }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {
                textureFrameReady = true;
                if (videoMessage != null) videoMessage.setVisibility(View.GONE);
            }
        });
        videoPane.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateVideoAspect());
        videoMessage = Ui.text(this, "Préparation de l'image…", 13, Ui.TEXT, true);
        videoMessage.setGravity(Gravity.CENTER);
        videoMessage.setPadding(d(18), d(12), d(18), d(12));
        videoMessage.setBackground(Ui.shape(0xCC15202B, 14, this));
        FrameLayout.LayoutParams msg = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        msg.setMargins(d(20), 0, d(20), 0);
        videoPane.addView(videoMessage, msg);
        LinearLayout videoTools = Ui.horizontal(this);
        videoTools.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        videoTools.setPadding(d(10), d(7), d(10), 0);
        videoFullButton = videoControl("⛶", "Afficher la vidéo en plein écran");
        videoFullButton.setOnClickListener(v -> enterVideoFullscreen());
        videoTools.addView(videoFullButton, Ui.lp(this, 45, 45));
        videoAudioButton = videoControl("♫", "Lecture audio de la vidéo uniquement");
        LinearLayout.LayoutParams ap = Ui.lp(this, 45, 45); ap.leftMargin = d(8);
        videoTools.addView(videoAudioButton, ap);
        videoAudioButton.setOnClickListener(v -> toggleAudioOnly());
        videoSpeedButton = videoControl("1×", "Vitesse de lecture");
        LinearLayout.LayoutParams sp = Ui.lp(this, 45, 45); sp.leftMargin = d(8);
        videoTools.addView(videoSpeedButton, sp);
        videoSpeedButton.setOnClickListener(v -> showVideoSpeedDialog());
        TextView moreVideo = videoControl("⋮", "Toutes les options vidéo");
        LinearLayout.LayoutParams moreP = Ui.lp(this, 45, 45); moreP.leftMargin = d(8);
        videoTools.addView(moreVideo, moreP);
        moreVideo.setOnClickListener(v -> showVideoOptions());
        videoPane.addView(videoTools, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
    }

    private void updateVideoMessage() {
        if (service == null || videoMessage == null || videoPane == null) return;
        int errorSerial = service.getVideoErrorSerial();
        if (errorSerial > lastShownVideoErrorSerial) {
            lastShownVideoErrorSerial = errorSerial;
            String error = service.getVideoErrorMessage();
            if (error != null && !error.isEmpty()) toast(error);
        }
        Track t = service.getCurrent();
        if (t == null || !t.isVideo || service.isVideoOnlyAudio()) {
            videoMessage.setVisibility(View.GONE); return;
        }
        if (t.id != observedVideoId) {
            observedVideoId = t.id;
            textureFrameReady = false;
            videoObservedElapsed = SystemClock.elapsedRealtime();
        }
        if (service.hasRenderedVideoFrame() || textureFrameReady) {
            videoMessage.setVisibility(View.GONE);
        } else {
            long elapsed = SystemClock.elapsedRealtime() - videoObservedElapsed;
            videoMessage.setText(elapsed < 5500
                    ? "Préparation de l'image vidéo…"
                    : "Image indisponible. Vérifie le codec de la vidéo ou utilise « Audio seul » dans les options ⋮.");
            videoMessage.setVisibility(View.VISIBLE);
        }
    }

    private void buildFullscreenVideoControls() {
        fullscreenControls = new FrameLayout(this);
        fullscreenControls.setVisibility(View.GONE);
        fullscreenControls.setClickable(false);
        fullOverlay.addView(fullscreenControls, new FrameLayout.LayoutParams(-1, -1));
        TextView exit = videoControl("✕", "Quitter le plein écran");
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(d(48), d(48), Gravity.TOP | Gravity.LEFT);
        ep.setMargins(d(14), d(18), 0, 0);
        fullscreenControls.addView(exit, ep);
        exit.setOnClickListener(v -> leaveVideoFullscreen());
        LinearLayout playerRow = Ui.horizontal(this);
        playerRow.setPadding(d(20), d(15), d(20), d(15));
        playerRow.setGravity(Gravity.CENTER_VERTICAL);
        playerRow.setBackground(Ui.gradient(0xEE18232D, 0xCC10191E, 14, this));
        TextView prev = videoControl("⏮", "Précédent");
        prev.setOnClickListener(v -> { if (service != null) service.previous(); });
        playerRow.addView(prev);
        fullscreenPause = videoControl("▶", "Pause ou lecture");
        fullscreenPause.setOnClickListener(v -> { if (service != null) service.toggle(); });
        playerRow.addView(fullscreenPause);
        TextView next = videoControl("⏭", "Suivant");
        next.setOnClickListener(v -> { if (service != null) service.next(); });
        playerRow.addView(next);
        fullscreenElapsed = Ui.text(this, "0:00", 11, Ui.TEXT, false);
        fullscreenElapsed.setPadding(d(7), 0, d(5), 0);
        playerRow.addView(fullscreenElapsed);
        fullscreenSeek = new SeekBar(this);
        fullscreenSeek.setMax(1000);
        fullscreenSeek.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        fullscreenSeek.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
        playerRow.addView(fullscreenSeek, new LinearLayout.LayoutParams(0, d(45), 1f));
        fullscreenSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) { fullscreenUserSeeking = true; }
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (fromUser && service != null && fullscreenElapsed != null)
                    fullscreenElapsed.setText(Track.time(service.getDuration() * value / 1000));
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (service != null) service.seek(service.getDuration() * bar.getProgress() / 1000);
                fullscreenUserSeeking = false;
            }
        });
        fullscreenTotal = Ui.text(this, "0:00", 11, Ui.TEXT, false);
        playerRow.addView(fullscreenTotal);
        TextView speed = videoControl("⋮", "Options vidéo");
        speed.setOnClickListener(v -> showVideoOptions());
        playerRow.addView(speed);
        FrameLayout.LayoutParams rowP = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        rowP.setMargins(d(12), 0, d(12), d(15));
        fullscreenControls.addView(playerRow, rowP);
    }

    private void updateVideoAspect() {
        if (videoSurface == null || videoPane == null || videoPane.getWidth() <= 0 || videoPane.getHeight() <= 0) return;
        int w = service == null ? 0 : service.getVideoWidth();
        int h = service == null ? 0 : service.getVideoHeight();
        int cw = videoPane.getWidth(), ch = videoPane.getHeight();
        int vw = cw, vh = ch;
        if (w > 0 && h > 0) {
            if ((long) cw * h > (long) ch * w) vw = Math.max(1, (int)((long) ch * w / h));
            else vh = Math.max(1, (int)((long) cw * h / w));
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) videoSurface.getLayoutParams();
        if (lp.width != vw || lp.height != vh || lp.gravity != Gravity.CENTER) {
            lp.width = vw; lp.height = vh; lp.gravity = Gravity.CENTER;
            videoSurface.setLayoutParams(lp);
        }
    }

    private void enterVideoFullscreen() {
        if (isVideoFullscreen || service == null || service.getCurrent() == null || !service.getCurrent().isVideo) return;
        isVideoFullscreen = true;
        ((ViewGroup) videoPane.getParent()).removeView(videoPane);
        fullScroll.setVisibility(View.GONE);
        fullOverlay.addView(videoPane, new FrameLayout.LayoutParams(-1, -1));
        videoPane.setVisibility(View.VISIBLE);
        fullscreenControls.setVisibility(View.VISIBLE);
        fullscreenControls.bringToFront();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        refreshPlayer();
    }

    private void leaveVideoFullscreen() {
        if (!isVideoFullscreen) return;
        isVideoFullscreen = false;
        if (videoPane.getParent() instanceof ViewGroup) ((ViewGroup) videoPane.getParent()).removeView(videoPane);
        artworkFrame.addView(videoPane, new FrameLayout.LayoutParams(-1, -1));
        fullScroll.setVisibility(View.VISIBLE);
        fullscreenControls.setVisibility(View.GONE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(0);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        refreshPlayer();
    }

    private void toggleAudioOnly() {
        if (service == null) return;
        boolean on = !service.isVideoOnlyAudio();
        service.setVideoOnlyAudio(on);
        videoSurface.setVisibility(on ? View.INVISIBLE : View.VISIBLE);
        updateVideoMessage();
        videoAudioButton.setTextColor(on ? Ui.ACCENT : Ui.TEXT);
        toast(on ? "Vidéo : lecture audio uniquement" : "Image vidéo réactivée");
    }

    private void showVideoSpeedDialog() {
        if (service == null) return;
        float[] speeds = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
        String[] labels = {"0,5×", "0,75×", "1× · Normale", "1,25×", "1,5×", "2×"};
        new AlertDialog.Builder(this).setTitle("Vitesse de lecture")
                .setSingleChoiceItems(labels, findSpeedIndex(speeds), (dialog, which) -> {
                    if (service != null) service.setPlaybackSpeed(speeds[which]);
                    dialog.dismiss();
                }).setNegativeButton("Annuler", null).show();
    }
    private int findSpeedIndex(float[] speeds) {
        float current = service == null ? 1f : service.getPlaybackSpeed();
        for (int i = 0; i < speeds.length; i++) if (Math.abs(speeds[i] - current) < 0.01f) return i;
        return 2;
    }
    private void showVideoDiagnostics() {
        if (service == null || service.getCurrent() == null) return;
        Track current = service.getCurrent();
        String format;
        try { format = getContentResolver().getType(current.uri); }
        catch (Exception e) { format = null; }
        new AlertDialog.Builder(this).setTitle("Diagnostic de la vidéo")
                .setMessage("Format : " + (format == null ? "inconnu" : format)
                        + "\nTaille détectée : " + service.getVideoWidth() + " × " + service.getVideoHeight()
                        + "\nImage rendue : " + (service.hasRenderedVideoFrame() || textureFrameReady ? "oui" : "non")
                        + "\nSurface disponible : " + (videoSurface != null && videoSurface.isAvailable() ? "oui" : "non")
                        + "\n\nSi l'image reste noire, essaye un MP4 H.264/AAC et communique-moi ces informations.")
                .setPositiveButton("Compris", null).show();
    }

    private void reattachVideoOutput() {
        if (service == null || videoSurface == null || !videoSurface.isAvailable()) {
            toast("Ouvre d'abord le lecteur vidéo"); return;
        }
        textureFrameReady = false;
        observedVideoId = Long.MIN_VALUE;
        service.setVideoSurface(null);
        attachVideoTextureIfReady();
        toast("Affichage vidéo réinitialisé");
    }

    private void showVideoOptions() {
        if (service == null || service.getCurrent() == null || !service.getCurrent().isVideo) return;
        Track current = service.getCurrent();
        List<OptionSheet.Item> actions = OptionSheet.items();
        actions.add(new OptionSheet.Item("⛶", isVideoFullscreen ? "Quitter le plein écran" : "Plein écran",
                "Afficher ou masquer les barres du système", () -> {
                    if (isVideoFullscreen) leaveVideoFullscreen(); else enterVideoFullscreen();
                }));
        actions.add(new OptionSheet.Item("♫", service.isVideoOnlyAudio() ? "Réactiver la vidéo" : "Écouter l’audio seul",
                "Continuer à écouter sans afficher l'image", this::toggleAudioOnly));
        actions.add(new OptionSheet.Item("1×", "Vitesse de lecture", "0,5× à 2×", this::showVideoSpeedDialog));
        actions.add(new OptionSheet.Item("↻", "Réinitialiser l'image", "Reconnecter la surface vidéo sans arrêter la lecture", this::reattachVideoOutput));
        actions.add(new OptionSheet.Item("ⓘ", "Diagnostic vidéo", "Format, résolution et état du rendu", this::showVideoDiagnostics));
        actions.add(new OptionSheet.Item("≋", "Égaliseur", "Régler le son de la vidéo", this::showEqualizerDialog));
        actions.add(new OptionSheet.Item("＋", "Ajouter à une playlist", "Playlists mixtes audio et vidéo", () -> addToPlaylist(current)));
        OptionSheet.show(this, "Options vidéo", current.title, actions);
    }

    private void openQueue() {
        hideFullPlayer();
        screen = "À suivre";
        clearDetail();
        rebuildTabs();
        render();
    }
    private void showFullPlayer() {
        if (service == null || service.getCurrent() == null) return;
        if (fullOverlay.getVisibility() == View.VISIBLE) return;
        closeKeyboard();
        refreshPlayer();
        fullOverlay.animate().cancel();
        fullOverlay.setVisibility(View.VISIBLE);
        fullOverlay.setAlpha(0f);
        fullOverlay.setTranslationY(d(48));
        fullOverlay.animate().alpha(1f).translationY(0f).setDuration(240)
                .setInterpolator(new DecelerateInterpolator()).start();
    }
    private void hideFullPlayer() {
        if (isVideoFullscreen) leaveVideoFullscreen();
        if (fullOverlay.getVisibility() != View.VISIBLE) return;
        fullOverlay.animate().cancel();
        fullOverlay.animate().alpha(0f).translationY(d(35)).setDuration(170)
                .withEndAction(() -> {
                    fullOverlay.setVisibility(View.GONE);
                    fullOverlay.setAlpha(1f);
                    fullOverlay.setTranslationY(0f);
                }).start();
    }

    private void refreshPlayer() {
        if (mini == null || fullOverlay == null) return;
        Track track = service == null ? null : service.getCurrent();
        if (track == null) {
            updateSleepBubble();
            miniCard.setVisibility(View.GONE);
            fullOverlay.setVisibility(View.GONE);
            return;
        }
        miniCard.setVisibility(View.VISIBLE);
        updateSleepBubble();
        miniTitle.setText(track.title);
        miniArtist.setText(track.isVideo ? "▶  VIDÉO  ·  " + track.artist : track.artist);
        fullTitle.setText(track.title);
        fullArtist.setText(track.isVideo ? "VIDÉO  •  " + track.album : track.artist + "  •  " + track.album);
        if (videoPane != null) videoPane.setVisibility(track.isVideo ? View.VISIBLE : View.GONE);
        if (isVideoFullscreen && !track.isVideo) leaveVideoFullscreen();
        if (videoSurface != null) videoSurface.setVisibility(service.isVideoOnlyAudio() ? View.INVISIBLE : View.VISIBLE);
        updateVideoMessage();
        if (videoAudioButton != null) videoAudioButton.setTextColor(service.isVideoOnlyAudio() ? Ui.ACCENT : Ui.TEXT);
        if (videoSpeedButton != null) videoSpeedButton.setText(service.getPlaybackSpeed() == 1f ? "1×" : "" + service.getPlaybackSpeed() + "×");
        if (fullscreenPause != null) fullscreenPause.setText(service.isPlaying() ? "Ⅱ" : "▶");
        if (videoPane != null && track.isVideo) {
            videoPane.post(this::updateVideoAspect);
            videoPane.post(this::attachVideoTextureIfReady);
        }
        // When a mixed playlist advances from audio to video, reveal the picture in foreground.
        if (!track.isVideo) lastPresentedVideoId = Long.MIN_VALUE;
        else if (service.isPlaying() && track.id != lastPresentedVideoId) {
            lastPresentedVideoId = track.id;
            if (fullOverlay.getVisibility() != View.VISIBLE) {
                long chosenId = track.id;
                root.postDelayed(() -> {
                    if (service != null && service.isPlaying() && service.getCurrent() != null
                            && service.getCurrent().id == chosenId && !isFinishing()) showFullPlayer();
                }, 150);
            }
        }
        if (!(miniCover.getTag() instanceof Long) || (Long) miniCover.getTag() != track.id) Artwork.bind(this, miniCover, track);
        if (!(fullCover.getTag() instanceof Long) || (Long) fullCover.getTag() != track.id) {
            fullOverlay.setBackground(Ui.gradient(0xFF202D33, Ui.BG, 0, this));
            Artwork.bind(this, fullCover, track, bitmap -> {
                if (service == null || service.getCurrent() == null || service.getCurrent().id != track.id) return;
                int sample = bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 3);
                int tinted = Color.rgb((int)(Color.red(sample) * 0.23f) + 14,
                        (int)(Color.green(sample) * 0.23f) + 15,
                        (int)(Color.blue(sample) * 0.23f) + 19);
                fullOverlay.setBackground(Ui.gradient(tinted, Ui.BG, 0, this));
            });
        }
        boolean isPlaying = service.isPlaying();
        miniPause.setText(isPlaying ? "Ⅱ" : "▶");
        fullPause.setText(isPlaying ? "Ⅱ" : "▶");
        shuffleButton.setTextColor(service.isShuffle() ? Ui.ACCENT : Ui.MUTED);
        shuffleButton.setBackground(Ui.shape(service.isShuffle() ? 0xFF293E2A : Color.TRANSPARENT, 24, this));
        int repeatMode = service.getRepeat();
        repeatButton.setText(repeatMode == 2 ? "↻₁" : "↻");
        repeatButton.setTextColor(repeatMode > 0 ? Ui.ACCENT : Ui.MUTED);
        repeatButton.setContentDescription(repeatMode == 0 ? "Répétition désactivée" : repeatMode == 1 ? "Répéter tous les morceaux" : "Répéter le morceau actuel");
        boolean favorite = store.isFavorite(track.id);
        heartButton.setText(favorite ? "♥" : "♡");
        heartButton.setTextColor(favorite ? Ui.ACCENT : Ui.MUTED);
        heartButton.setContentDescription(favorite ? "Retirer des favoris" : "Ajouter aux favoris");
        List<Track> coming = service.upcoming();
        fullQueueCount.setText(coming.size() + (coming.size() > 1 ? " morceaux à suivre" : " morceau à suivre"));
        updateProgress();
        if ((screen.equals("À suivre") || screen.equals("Écoutés")) && hasScanned
                && fullOverlay.getVisibility() != View.VISIBLE) render();
    }
    private void updateProgress() {
        if (service == null || service.getCurrent() == null || progress == null) return;
        long duration = service.getDuration();
        long position = service.getPosition();
        if (!userSeeking) {
            progress.setProgress(duration > 0 ? (int) Math.min(1000L, position * 1000L / duration) : 0);
            elapsed.setText(Track.time(position));
        }
        total.setText(Track.time(duration));
        if (isVideoFullscreen && fullscreenSeek != null) {
            if (!fullscreenUserSeeking) {
                fullscreenSeek.setProgress(duration > 0 ? (int) Math.min(1000L, position * 1000L / duration) : 0);
                fullscreenElapsed.setText(Track.time(position));
            }
            fullscreenTotal.setText(Track.time(duration));
        }
        if (miniProgress != null && miniCard != null && miniCard.getWidth() > 0) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) miniProgress.getLayoutParams();
            int width = duration > 0 ? (int) (miniCard.getWidth() * Math.min(1d, position / (double) duration)) : 0;
            if (params.width != width) { params.width = width; miniProgress.setLayoutParams(params); }
        }
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (videoPane != null) videoPane.post(this::updateVideoAspect);
    }

    @Override public void onBackPressed() {
        if (isVideoFullscreen) { leaveVideoFullscreen(); return; }
        if (fullOverlay != null && fullOverlay.getVisibility() == View.VISIBLE) {
            hideFullPlayer();
            return;
        }
        if (detailAlbum != null || detailArtist != null || detailPlaylist != null) { clearAndRender(); return; }
        if (!screen.equals("Titres")) {
            screen = "Titres";
            rebuildTabs(); render();
            return;
        }
        super.onBackPressed();
    }
}
