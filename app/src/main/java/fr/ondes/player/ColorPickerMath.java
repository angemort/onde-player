package fr.ondes.player;

/** Coordinate conversions for the theme picker; Android-independent for unit testing. */
public final class ColorPickerMath {
    private ColorPickerMath() { }

    public static float clamp01(float value) {
        if (Float.isNaN(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }

    public static float saturation(float x, float left, float right) {
        return right <= left ? 0f : clamp01((x - left) / (right - left));
    }

    public static float brightness(float y, float top, float bottom) {
        return bottom <= top ? 1f : 1f - clamp01((y - top) / (bottom - top));
    }

    public static float hue(float x, float left, float right) {
        return right <= left ? 0f : 359.999f * clamp01((x - left) / (right - left));
    }
}
