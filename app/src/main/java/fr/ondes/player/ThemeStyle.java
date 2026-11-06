package fr.ondes.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Shared theme surface renderer. All three roles (accent, background, text)
 * support a solid color, two stops, or a local picture chosen by SAF.
 * Image bitmaps are sampled, cached, never uploaded, and never referenced by path.
 */
public final class ThemeStyle {
    private static final Map<String, Bitmap> IMAGES = new HashMap<>();
    private static final WeakHashMap<TextView, Boolean> TEXT_HOOKS = new WeakHashMap<>();
    private ThemeStyle() {}

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("onde_settings", Context.MODE_PRIVATE);
    }
    public static int defaultColor(String role) {
        if ("accent".equals(role)) return 0xFFA8F36A;
        if ("text".equals(role)) return 0xFFF5F8FB;
        return 0xFF0B0C12;
    }
    public static int solid(Context c, String role) {
        return prefs(c).getInt("theme_" + role, defaultColor(role));
    }
    public static String imageUri(Context c, String role) {
        SharedPreferences p = prefs(c);
        String uri = p.getString("theme_" + role + "_image", "");
        if ((uri == null || uri.isEmpty()) && "background".equals(role))
            uri = p.getString("theme_wallpaper", "");
        return uri == null ? "" : uri;
    }
    public static String mode(Context c, String role) {
        SharedPreferences p = prefs(c);
        String explicit = p.getString("theme_" + role + "_mode", "");
        if ("solid".equals(explicit) || "gradient".equals(explicit) || "image".equals(explicit))
            return explicit;
        // Existing 3.7 themes are migrated lazily without changing their saved values.
        if ("background".equals(role)) {
            if (!imageUri(c, role).isEmpty()) return "image";
            if (p.getBoolean("theme_gradient_enabled", false)) return "gradient";
        }
        return "solid";
    }
    public static int start(Context c, String role) {
        SharedPreferences p = prefs(c);
        int fallback = solid(c, role);
        if ("background".equals(role))
            fallback = p.getInt("theme_gradient_start", ThemeGradient.STARTS[0]);
        return p.getInt("theme_" + role + "_start", fallback);
    }
    public static int end(Context c, String role) {
        SharedPreferences p = prefs(c);
        int fallback = "accent".equals(role) ? 0xFF9B82F5
                : "text".equals(role) ? 0xFF79D3EE : ThemeGradient.ENDS[0];
        if ("background".equals(role))
            fallback = p.getInt("theme_gradient_end", fallback);
        return p.getInt("theme_" + role + "_end", fallback);
    }
    public static int angle(Context c, String role) {
        SharedPreferences p = prefs(c);
        int fallback = "background".equals(role) ? p.getInt("theme_gradient_angle", 315) : 315;
        return p.getInt("theme_" + role + "_angle", fallback);
    }
    public static int strength(Context c, String role) {
        SharedPreferences p = prefs(c);
        int fallback = "background".equals(role) ? p.getInt("theme_gradient_strength", 85) : 100;
        return ThemeGradient.clamp(p.getInt("theme_" + role + "_strength", fallback), 0, 100);
    }
    public static int[] stops(Context c, String role) {
        int base = "background".equals(role) ? Ui.effectiveBackground(solid(c, role)) : solid(c, role);
        int a = start(c, role), b = end(c, role);
        if ("background".equals(role)) {
            a = Ui.effectiveBackground(a);
            b = Ui.effectiveBackground(b);
        }
        float amount = strength(c, role) / 100f;
        return new int[]{ThemeGradient.blend(base, a, amount), ThemeGradient.blend(base, b, amount)};
    }
    public static GradientDrawable.Orientation orientation(int angle) {
        GradientDrawable.Orientation[] values = {
                GradientDrawable.Orientation.LEFT_RIGHT,
                GradientDrawable.Orientation.BL_TR,
                GradientDrawable.Orientation.BOTTOM_TOP,
                GradientDrawable.Orientation.BR_TL,
                GradientDrawable.Orientation.RIGHT_LEFT,
                GradientDrawable.Orientation.TR_BL,
                GradientDrawable.Orientation.TOP_BOTTOM,
                GradientDrawable.Orientation.TL_BR
        };
        return values[Math.floorMod(Math.round(angle / 45f), 8)];
    }
    public static Bitmap picture(Context c, String role) {
        String raw = imageUri(c, role);
        if (raw.isEmpty()) return null;
        synchronized (IMAGES) {
            if (IMAGES.containsKey(raw)) return IMAGES.get(raw);
        }
        Bitmap decoded = null;
        try {
            Uri uri = Uri.parse(raw);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                int sample = 1;
                while (bounds.outWidth / sample > 960 || bounds.outHeight / sample > 1280)
                    sample *= 2;
                options.inSampleSize = sample;
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                    decoded = BitmapFactory.decodeStream(in, null, options);
                }
            }
        } catch (Exception ignored) {
            // Android can revoke grants: preserve a readable solid fallback.
        }
        synchronized (IMAGES) {
            if (IMAGES.size() > 5) IMAGES.clear();
            IMAGES.put(raw, decoded);
        }
        return decoded;
    }
    private static BitmapShader pictureShader(Bitmap bitmap, float width, float height) {
        BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float scale = Math.max(width / Math.max(1, bitmap.getWidth()),
                               height / Math.max(1, bitmap.getHeight()));
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate((width - bitmap.getWidth() * scale) / 2f,
                             (height - bitmap.getHeight() * scale) / 2f);
        shader.setLocalMatrix(matrix);
        return shader;
    }
    private static Shader gradientShader(int[] colors, int angle, float width, float height) {
        double radians = Math.toRadians(angle);
        float dx = (float)Math.cos(radians) * width / 2f;
        float dy = (float)Math.sin(radians) * height / 2f;
        if (Math.abs(dx) + Math.abs(dy) < .001f) dx = width / 2f;
        return new LinearGradient(width/2f-dx, height/2f-dy,
                width/2f+dx, height/2f+dy, colors[0], colors[1], Shader.TileMode.CLAMP);
    }
    public static Drawable surface(Context c, String role, float radiusDp) {
        String mode = mode(c, role);
        int fallback = "background".equals(role)
                ? Ui.effectiveBackground(solid(c, role)) : solid(c, role);
        if ("gradient".equals(mode)) {
            GradientDrawable g = new GradientDrawable(orientation(angle(c, role)), stops(c, role));
            g.setCornerRadius(Ui.dp(c, radiusDp));
            return g;
        }
        if ("image".equals(mode)) {
            Bitmap bitmap = picture(c, role);
            if (bitmap != null) {
                PaintDrawable image = new PaintDrawable(fallback);
                image.setCornerRadius(Ui.dp(c, radiusDp));
                image.setShaderFactory(new ShapeDrawable.ShaderFactory() {
                    @Override public Shader resize(int w, int h) {
                        return pictureShader(bitmap, Math.max(1f, w), Math.max(1f, h));
                    }
                });
                return image;
            }
        }
        GradientDrawable solid = new GradientDrawable();
        solid.setColor(fallback);
        solid.setCornerRadius(Ui.dp(c, radiusDp));
        return solid;
    }
    /** Paint shaders are applied to actual text, not just swatches in the editor. */
    public static void applyText(TextView view, String role) {
        if (view == null) return;
        Context c = view.getContext();
        String mode = mode(c, role);
        if ("solid".equals(mode)) {
            view.getPaint().setShader(null);
            view.invalidate();
            return;
        }
        if (!TEXT_HOOKS.containsKey(view)) {
            TEXT_HOOKS.put(view, true);
            view.addOnLayoutChangeListener((v, left, top, right, bottom, ol, ot, oright, ob) -> {
                String currentMode = mode(v.getContext(), role);
                if (!"solid".equals(currentMode))
                    applyTextShader((TextView)v, role);
            });
        }
        applyTextShader(view, role);
    }
    private static void applyTextShader(TextView view, String role) {
        Context c = view.getContext();
        String mode = mode(c, role);
        int width = Math.max(1, view.getWidth() - view.getPaddingLeft() - view.getPaddingRight());
        int height = Math.max(1, view.getHeight() - view.getPaddingTop() - view.getPaddingBottom());
        Shader shader = null;
        if ("gradient".equals(mode))
            shader = gradientShader(stops(c, role), angle(c, role), width, height);
        else if ("image".equals(mode)) {
            Bitmap bitmap = picture(c, role);
            if (bitmap != null) shader = pictureShader(bitmap, width, height);
        }
        view.getPaint().setShader(shader);
        view.invalidate();
    }
}
