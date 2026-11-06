package fr.ondes.player;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Size;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Async cover-art lookup with a bounded in-memory cache. */
public final class Artwork {
    private static final LruCache<Long, Bitmap> CACHE = new LruCache<Long, Bitmap>(16 * 1024) {
        @Override protected int sizeOf(Long key, Bitmap value) { return value.getByteCount() / 1024; }
    };
    private static final Set<Long> NO_COVER = Collections.synchronizedSet(new HashSet<>());
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private Artwork() {}

    public static void bind(Context context, ImageView image, Track track) {
        bind(context, image, track, null);
    }
    public static void bind(Context context, ImageView image, Track track, Consumer<Bitmap> onLoaded) {
        if (track == null) { fallback(image); return; }
        image.setTag(track.id);
        Bitmap cached = CACHE.get(track.id);
        if (cached != null) { show(image, cached); if (onLoaded != null) onLoaded.accept(cached); return; }
        fallback(image);
        if (NO_COVER.contains(track.id)) return;
        WeakReference<ImageView> ref = new WeakReference<>(image);
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            Bitmap bitmap = load(app, track);
            if (bitmap != null) CACHE.put(track.id, bitmap);
            else NO_COVER.add(track.id);
            MAIN.post(() -> {
                ImageView target = ref.get();
                if (target != null && target.getTag() instanceof Long && (Long) target.getTag() == track.id && bitmap != null) {
                    show(target, bitmap);
                    if (onLoaded != null) onLoaded.accept(bitmap);
                }
            });
        });
    }
    public static void request(Context context, Track track, Consumer<Bitmap> callback) {
        if (track == null || callback == null) return;
        Bitmap cached = CACHE.get(track.id);
        if (cached != null) { MAIN.post(() -> callback.accept(cached)); return; }
        if (NO_COVER.contains(track.id)) return;
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            Bitmap bitmap = load(app, track);
            if (bitmap != null) CACHE.put(track.id, bitmap);
            else NO_COVER.add(track.id);
            if (bitmap != null) MAIN.post(() -> callback.accept(bitmap));
        });
    }
    private static void fallback(ImageView image) {
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setImageResource(R.drawable.ic_notification);
        image.setColorFilter(Ui.ACCENT);
        image.setPadding(Ui.dp(image.getContext(), 14), Ui.dp(image.getContext(), 14),
                Ui.dp(image.getContext(), 14), Ui.dp(image.getContext(), 14));
    }
    private static void show(ImageView image, Bitmap bitmap) {
        image.clearColorFilter();
        image.setPadding(0, 0, 0, 0);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImageBitmap(bitmap);
    }
    private static Bitmap load(Context context, Track track) {
        if (track.isVideo) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    return context.getContentResolver().loadThumbnail(track.uri, new Size(360, 240), null);
                }
                MediaMetadataRetriever video = new MediaMetadataRetriever();
                try {
                    video.setDataSource(context, track.uri);
                    Bitmap frame = video.getFrameAtTime(1000000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (frame != null) {
                        int width = Math.max(1, frame.getWidth());
                        int height = Math.max(1, frame.getHeight());
                        float scale = Math.min(1f, 360f / Math.max(width, height));
                        Bitmap smaller = Bitmap.createScaledBitmap(frame,
                                Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)), true);
                        if (smaller != frame) frame.recycle();
                        return smaller;
                    }
                } finally { video.release(); }
            } catch (Exception ignored) { }
            return null;
        }
        if (track.albumId > 0) {
            Uri albumUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), track.albumId);
            try (InputStream stream = context.getContentResolver().openInputStream(albumUri)) {
                if (stream != null) {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(stream, null, bounds);
                    BitmapFactory.Options opt = new BitmapFactory.Options();
                    while (bounds.outWidth / opt.inSampleSize > 384 || bounds.outHeight / opt.inSampleSize > 384) opt.inSampleSize *= 2;
                    try (InputStream scaled = context.getContentResolver().openInputStream(albumUri)) {
                        if (scaled != null) {
                            Bitmap bitmap = BitmapFactory.decodeStream(scaled, null, opt);
                            if (bitmap != null) return bitmap;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, track.uri);
            byte[] bytes = retriever.getEmbeddedPicture();
            if (bytes == null) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 384 || options.outHeight / options.inSampleSize > 384) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (Exception ignored) { return null; }
        finally { try { retriever.release(); } catch (Exception ignored) {} }
    }
}
