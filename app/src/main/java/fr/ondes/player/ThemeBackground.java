package fr.ondes.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.io.InputStream;

/** Memory-bounded, low-contrast wallpaper underneath interactive content. */
public final class ThemeBackground {
    private ThemeBackground() {}
    public static void attach(Context context, FrameLayout parent) {
        String saved = context.getSharedPreferences("onde_settings", Context.MODE_PRIVATE)
                .getString("theme_wallpaper", "");
        if (saved == null || saved.isEmpty()) return;
        try {
            Uri uri = Uri.parse(saved);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / sample > 1250 || bounds.outHeight / sample > 2000) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, options);
            }
            if (bitmap == null) return;
            ImageView image = new ImageView(context);
            image.setImageBitmap(bitmap);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setAlpha(.23f);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            parent.addView(image, 0, new FrameLayout.LayoutParams(-1, -1));
        } catch (Exception ignored) { /* Missing or revoked image; the solid theme remains usable. */ }
    }
}
