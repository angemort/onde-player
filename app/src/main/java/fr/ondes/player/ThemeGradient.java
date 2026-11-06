package fr.ondes.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/** Accessible two-color gradients. User artwork and all options remain strictly local. */
public final class ThemeGradient {
    private ThemeGradient() {}

    public static final String[] NAMES = {
            "Nuit électrique", "Océan", "Aurore", "Braise", "Forêt", "Prune"
    };
    public static final int[] STARTS = {
            0xFF171331, 0xFF09243A, 0xFF332044, 0xFF37201D, 0xFF102D24, 0xFF2F1838
    };
    public static final int[] ENDS = {
            0xFF063F43, 0xFF192550, 0xFF243C59, 0xFF4A1D36, 0xFF20283E, 0xFF122D43
    };

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    public static int blend(int a, int b, float weight) {
        float t = Math.max(0f, Math.min(1f, weight));
        return Color.rgb(Math.round(Color.red(a) * (1f-t) + Color.red(b) * t),
                Math.round(Color.green(a) * (1f-t) + Color.green(b) * t),
                Math.round(Color.blue(a) * (1f-t) + Color.blue(b) * t));
    }
    public static GradientDrawable create(Context context, int base, int start, int end,
                                          int angle, int strength, float radiusDp) {
        int first = blend(base, Ui.effectiveBackground(start), clamp(strength, 0, 100) / 100f);
        int second = blend(base, Ui.effectiveBackground(end), clamp(strength, 0, 100) / 100f);
        GradientDrawable.Orientation[] directions = {
                GradientDrawable.Orientation.LEFT_RIGHT,
                GradientDrawable.Orientation.BL_TR,
                GradientDrawable.Orientation.BOTTOM_TOP,
                GradientDrawable.Orientation.BR_TL,
                GradientDrawable.Orientation.RIGHT_LEFT,
                GradientDrawable.Orientation.TR_BL,
                GradientDrawable.Orientation.TOP_BOTTOM,
                GradientDrawable.Orientation.TL_BR
        };
        int direction = ((angle / 45) % 8 + 8) % 8;
        GradientDrawable drawable = new GradientDrawable(directions[direction], new int[]{first, second});
        drawable.setCornerRadius(Ui.dp(context, radiusDp));
        return drawable;
    }
    public static void apply(Context context, android.view.View root) {
        SharedPreferences preferences = context.getSharedPreferences("onde_settings", Context.MODE_PRIVATE);
        if (!preferences.getBoolean("theme_gradient_enabled", false)) {
            root.setBackgroundColor(Ui.BG);
            return;
        }
        root.setBackground(create(context, Ui.BG,
                preferences.getInt("theme_gradient_start", STARTS[0]),
                preferences.getInt("theme_gradient_end", ENDS[0]),
                preferences.getInt("theme_gradient_angle", 7 * 45),
                preferences.getInt("theme_gradient_strength", 85), 0));
    }
}
