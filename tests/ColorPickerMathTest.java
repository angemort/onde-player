import fr.ondes.player.ColorPickerMath;

public final class ColorPickerMathTest {
    private static void close(float actual, float expected) {
        if (Math.abs(actual - expected) > .001f)
            throw new AssertionError("expected " + expected + ", got " + actual);
    }
    public static void main(String[] args) {
        close(ColorPickerMath.saturation(0, 0, 100), 0);
        close(ColorPickerMath.saturation(50, 0, 100), .5f);
        close(ColorPickerMath.saturation(110, 0, 100), 1);
        close(ColorPickerMath.saturation(-15, 0, 100), 0);
        close(ColorPickerMath.brightness(10, 10, 210), 1);
        close(ColorPickerMath.brightness(110, 10, 210), .5f);
        close(ColorPickerMath.brightness(210, 10, 210), 0);
        close(ColorPickerMath.hue(50, 0, 100), 179.9995f);
        close(ColorPickerMath.hue(200, 0, 100), 359.999f);
        close(ColorPickerMath.clamp01(Float.NaN), 0);
        close(ColorPickerMath.saturation(10, 10, 10), 0);
        close(ColorPickerMath.brightness(10, 10, 10), 1);
        System.out.println("ColorPickerMath: 12 assertions OK");
    }
}
