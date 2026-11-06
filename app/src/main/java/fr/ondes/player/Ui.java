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
    public static final int PURPLE = Color.rgb(155, 130, 245);
    private Ui() {}

    public static void configure(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("onde_settings", Context.MODE_PRIVATE);
        ACCENT = prefs.getInt("theme_accent", 0xFFA8F36A);
        BG = prefs.getInt("theme_background", 0xFF0B0C12);
        BG = effectiveBackground(BG);
        SURFACE = blend(BG, 0xFFFFFFFF, .075f);
        SURFACE_ALT = blend(BG, 0xFFFFFFFF, .13f);
        TEXT = prefs.getInt("theme_text", 0xFFF5F8FB);
        MUTED = prefs.contains("theme_text") ? blend(BG, TEXT, .63f) : 0xFF9CA4B1;
    }
    /** Match theme-editor previews to the background actually used in the player. */
    public static int effectiveBackground(int color) {
        float luminance = (Color.red(color) * .2126f + Color.green(color) * .7152f + Color.blue(color) * .0722f);
        if (luminance <= 90f) return color;
        float factor = 90f / luminance;
        return Color.rgb(Math.round(Color.red(color) * factor),
                Math.round(Color.green(color) * factor), Math.round(Color.blue(color) * factor));
    }
    private static int blend(int a, int b, float ratio) {
        return Color.rgb((int)(Color.red(a)*(1-ratio)+Color.red(b)*ratio),
                (int)(Color.green(a)*(1-ratio)+Color.green(b)*ratio),
                (int)(Color.blue(a)*(1-ratio)+Color.blue(b)*ratio));
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
    public static void clickable(View v, android.graphics.drawable.Drawable normal) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), normal, null));
    }
    public static StateListDrawable activatedRow(Context c) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_activated}, shape(0xFF294138, 12, c));
        d.addState(new int[]{android.R.attr.state_pressed}, shape(0xFF252E38, 12, c));
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
        v.setBackgroundColor(0x22FFFFFF);
        v.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1)));
        return v;
    }
    public static void pad(View v, Context c, int h, int vert) {
        v.setPadding(dp(c, h), dp(c, vert), dp(c, h), dp(c, vert));
    }
}
