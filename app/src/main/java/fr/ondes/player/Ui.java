package fr.ondes.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small dependency-free design system for the dark music player. */
public final class Ui {
    public static int BG = Color.rgb(11, 12, 18);
    public static int SURFACE = Color.rgb(24, 27, 37);
    public static int SURFACE_ALT = Color.rgb(31, 35, 46);
    public static int TEXT = Color.rgb(245, 248, 251);
    public static int MUTED = Color.rgb(156, 164, 177);
    public static int ACCENT = Color.rgb(168, 243, 106);
    /** All auxiliary tokens are derived from the three user-editable roles. */
    public static int PURPLE = Color.rgb(155, 130, 245);
    public static int SECONDARY = Color.rgb(155, 130, 245);
    public static int HOVER = Color.rgb(43, 54, 67);
    public static int PRESSED = Color.rgb(47, 61, 67);
    public static int SELECTION = Color.rgb(40, 63, 55);
    public static int ACCENT_HOVER = Color.rgb(140, 224, 88);
    public static int ACCENT_PRESSED = Color.rgb(115, 200, 77);
    public static int ACCENT_SECONDARY = Color.rgb(40, 61, 48);
    public static int ACCENT_SUBTLE = Color.rgb(30, 45, 36);
    public static int ACCENT_OUTLINE = Color.rgb(70, 101, 58);
    public static int OUTLINE = Color.rgb(76, 83, 95);
    public static int DIVIDER = Color.rgb(53, 58, 67);
    public static int ON_ACCENT = Color.rgb(16, 24, 32);
    public static int RIPPLE = 0x44A8F36A;
    public static int RIPPLE_ON_ACCENT = 0x44101820;
    private Ui() {}

    public static void configure(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("onde_settings", Context.MODE_PRIVATE);
        BG = effectiveBackground(ThemeStyle.solid(context, "background"));
        ACCENT = ThemeStyle.representativeColor(context, "accent");
        TEXT = ThemeStyle.representativeColor(context, "text");
        ThemePalette.Colors palette = ThemePalette.derive(ACCENT, BG, TEXT);
        SURFACE = palette.surface;
        SURFACE_ALT = palette.surfaceAlt;
        HOVER = palette.surfaceHover;
        PRESSED = palette.surfacePressed;
        SELECTION = palette.selection;
        MUTED = palette.muted;
        OUTLINE = palette.outline;
        DIVIDER = palette.divider;
        ACCENT_HOVER = palette.accentHover;
        ACCENT_PRESSED = palette.accentPressed;
        ACCENT_SECONDARY = palette.accentSecondary;
        ACCENT_SUBTLE = palette.accentSubtle;
        ACCENT_OUTLINE = palette.accentOutline;
        ON_ACCENT = palette.onAccent;
        RIPPLE = palette.ripple;
        RIPPLE_ON_ACCENT = palette.rippleOnAccent;
        SECONDARY = palette.secondaryAccent;
        PURPLE = SECONDARY; // Backward compatibility for older secondary controls.
    }
    /** Match theme-editor previews to the background actually used in the player. */
    public static int effectiveBackground(int color) {
        float luminance = (Color.red(color) * .2126f + Color.green(color) * .7152f + Color.blue(color) * .0722f);
        if (luminance <= 90f) return color;
        float factor = 90f / luminance;
        return Color.rgb(Math.round(Color.red(color) * factor),
                Math.round(Color.green(color) * factor), Math.round(Color.blue(color) * factor));
    }
    public static int dp(Context context, float dp) {
        return (int) (context.getResources().getDisplayMetrics().density * dp + 0.5f);
    }
    public static Drawable shape(int color, float radius, Context c) {
        // A theme accent paints buttons and indicators too, not just the preview.
        if (color == ACCENT && !"solid".equals(ThemeStyle.mode(c, "accent")))
            return ThemeStyle.surface(c, "accent", radius);
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radius));
        return d;
    }
    public static GradientDrawable outlined(int color, int stroke, float radius, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radius));
        d.setStroke(dp(c, 1), stroke);
        return d;
    }
    public static GradientDrawable gradient(int a, int b, float radius, Context c) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        d.setCornerRadius(dp(c, radius));
        return d;
    }
    public static void clickable(View v, Drawable normal) {
        clickable(v, normal, false);
    }
    public static void clickable(View v, Drawable normal, boolean onAccent) {
        int pressedTint = onAccent ? RIPPLE_ON_ACCENT : RIPPLE;
        int hoverTint = onAccent ? (RIPPLE_ON_ACCENT & 0x00FFFFFF) | 0x22000000
                : (RIPPLE & 0x00FFFFFF) | 0x22000000;
        ColorStateList states = new ColorStateList(new int[][] {
                new int[]{android.R.attr.state_pressed},
                new int[]{android.R.attr.state_focused},
                new int[]{android.R.attr.state_hovered},
                new int[]{}
        }, new int[]{pressedTint,pressedTint,hoverTint,pressedTint});
        v.setBackground(new RippleDrawable(states, normal, null));
    }
    public static StateListDrawable activatedRow(Context c) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_activated}, shape(SELECTION, 12, c));
        d.addState(new int[]{android.R.attr.state_pressed}, shape(PRESSED, 12, c));
        d.addState(new int[]{android.R.attr.state_hovered}, shape(HOVER, 12, c));
        d.addState(new int[]{}, shape(Color.TRANSPARENT, 12, c));
        return d;
    }
    public static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    public static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    public static TextView text(Context c, String text, float size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if (color == ACCENT) ThemeStyle.applyText(t, "accent");
        else if (color == TEXT) ThemeStyle.applyText(t, "text");
        return t;
    }
    public static TextView icon(Context c, String glyph, int sizeDp, int fontSize, int textColor, int background) {
        TextView t = new IconTextView(c);
        t.setText(glyph);
        t.setTextSize(fontSize);
        t.setTextColor(textColor);
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        clickable(t, shape(background, sizeDp / 2f, c));
        t.setContentDescription(glyph);
        if (textColor == ACCENT) ThemeStyle.applyText(t, "accent");
        else if (textColor == TEXT) ThemeStyle.applyText(t, "text");
        return t;
    }
    public static LinearLayout.LayoutParams lp(Context c, int width, int height) {
        return new LinearLayout.LayoutParams(width < 0 ? width : dp(c, width), height < 0 ? height : dp(c, height));
    }
    public static LinearLayout.LayoutParams weighted(Context c, int height) {
        return new LinearLayout.LayoutParams(0, height < 0 ? height : dp(c, height), 1f);
    }
    public static View gap(Context c, int height) {
        View v = new View(c);
        v.setLayoutParams(lp(c, 1, height));
        return v;
    }
    public static View line(Context c) {
        View v = new View(c);
        v.setBackgroundColor(DIVIDER);
        v.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1)));
        return v;
    }
    public static void pad(View v, Context c, int h, int vert) {
        v.setPadding(dp(c, h), dp(c, vert), dp(c, h), dp(c, vert));
    }
}
