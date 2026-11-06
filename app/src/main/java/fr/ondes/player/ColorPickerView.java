package fr.ondes.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Touch-operated HSV color picker with a saturation/brightness field and a rainbow hue bar. */
public final class ColorPickerView extends View {
    public interface Listener {
        /** Called while dragging for preview and once with finished=true on finger release. */
        void onColorChanged(int color, boolean finished);
    }

    private static final int AREA_NONE = 0;
    private static final int AREA_SHADE = 1;
    private static final int AREA_HUE = 2;
    private static final int[] HUE_COLORS = {
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
            Color.BLUE, Color.MAGENTA, Color.RED
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF shade = new RectF();
    private final RectF hueBar = new RectF();
    private final float[] hsv = {100f, .7f, .9f};
    private final float[] startHsv = new float[3];
    private Listener listener;
    private int tracking = AREA_NONE;
    private int previewColor;

    public ColorPickerView(Context context) { super(context); init(); }
    public ColorPickerView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setFocusable(true);
        setContentDescription("Choisir une couleur : carré des nuances et barre des teintes");
        previewColor = Color.HSVToColor(hsv);
    }

    private float d(float value) { return Ui.dp(getContext(), value); }

    /** Synchronize the picker with the color saved for the current tab, without invoking the listener. */
    public void setColor(int color) {
        Color.colorToHSV(color, hsv);
        previewColor = Color.HSVToColor(hsv);
        invalidate();
    }

    public int getColor() { return previewColor; }
    public void setListener(Listener listener) { this.listener = listener; }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        float inset = d(9);
        shade.set(inset, d(9), Math.max(inset + 1f, w - inset), Math.max(d(10), h - d(77)));
        hueBar.set(inset + d(2), h - d(46), Math.max(inset + d(3), w - inset - d(2)), h - d(26));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float corner = d(14);
        int vividHue = Color.HSVToColor(new float[]{hsv[0], 1f, 1f});

        // Saturation increases from left to right, brightness decreases toward the bottom.
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(shade.left, shade.top, shade.right, shade.top,
                Color.WHITE, vividHue, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(shade, corner, corner, paint);
        paint.setShader(new LinearGradient(shade.left, shade.top, shade.left, shade.bottom,
                Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(shade, corner, corner, paint);
        paint.setShader(null);

        // Thin border keeps dark picker edges visible on all themes.
        paint.setColor(0x667D8796);
        paint.setStrokeWidth(d(1));
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRoundRect(shade, corner, corner, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setShader(new LinearGradient(hueBar.left, hueBar.top, hueBar.right, hueBar.top,
                HUE_COLORS, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(hueBar, d(10), d(10), paint);
        paint.setShader(null);
        paint.setColor(0x667D8796);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRoundRect(hueBar, d(10), d(10), paint);
        paint.setStyle(Paint.Style.FILL);

        float cx = shade.left + shade.width() * hsv[1];
        float cy = shade.top + shade.height() * (1f - hsv[2]);
        drawHandle(canvas, cx, cy, d(10), previewColor);
        float hueX = hueBar.left + hueBar.width() * (hsv[0] / 360f);
        drawHandle(canvas, hueX, hueBar.centerY(), d(12), vividHue);
    }

    private void drawHandle(Canvas canvas, float x, float y, float radius, int fill) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        canvas.drawCircle(x, y, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xDD111820);
        paint.setStrokeWidth(d(4));
        canvas.drawCircle(x, y, radius + d(1.4f), paint);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(d(2.4f));
        canvas.drawCircle(x, y, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                float x = event.getX(), y = event.getY();
                if (x < shade.left - d(18) || x > shade.right + d(18)) return false;
                if (y >= shade.top - d(16) && y <= shade.bottom + d(12)) tracking = AREA_SHADE;
                else if (y >= hueBar.top - d(20) && y <= hueBar.bottom + d(20)) tracking = AREA_HUE;
                else return false;
                System.arraycopy(hsv, 0, startHsv, 0, hsv.length);
                getParent().requestDisallowInterceptTouchEvent(true);
                updateFrom(event.getX(), event.getY(), false);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (tracking == AREA_NONE) return false;
                updateFrom(event.getX(), event.getY(), false);
                return true;
            case MotionEvent.ACTION_UP:
                if (tracking == AREA_NONE) return false;
                updateFrom(event.getX(), event.getY(), true);
                tracking = AREA_NONE;
                getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (tracking == AREA_NONE) return false;
                System.arraycopy(startHsv, 0, hsv, 0, hsv.length);
                previewColor = Color.HSVToColor(hsv);
                invalidate();
                if (listener != null) listener.onColorChanged(previewColor, false);
                tracking = AREA_NONE;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return false;
        }
    }

    private void updateFrom(float x, float y, boolean finished) {
        if (tracking == AREA_SHADE) {
            hsv[1] = ColorPickerMath.saturation(x, shade.left, shade.right);
            hsv[2] = ColorPickerMath.brightness(y, shade.top, shade.bottom);
        } else if (tracking == AREA_HUE) {
            hsv[0] = ColorPickerMath.hue(x, hueBar.left, hueBar.right);
            // Allow selecting a hue even if the current theme was gray or pure black.
            if (hsv[1] < .08f) hsv[1] = .8f;
            if (hsv[2] < .08f) hsv[2] = .8f;
        }
        previewColor = Color.HSVToColor(hsv);
        invalidate();
        if (listener != null) listener.onColorChanged(previewColor, finished);
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
