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

/**
 * One HSV picker for two gradient stops. Both stop handles stay visible in
 * the saturation/brightness square and on the hue bar. The active stop is
 * selected by touching its handle (or by the 1/2 accessibility buttons).
 *
 * In particular no parent view or activity is rebuilt from onTouchEvent:
 * the final callback is posted after ACTION_UP has finished dispatching.
 */
public final class DualColorPickerView extends View {
    public interface Listener {
        void onColorsChanged(int first, int second, int activeStop, boolean finished);
    }

    private static final int AREA_NONE = 0;
    private static final int AREA_SHADE = 1;
    private static final int AREA_HUE = 2;
    private static final int[] RAINBOW = {
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
            Color.BLUE, Color.MAGENTA, Color.RED
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF shade = new RectF();
    private final RectF hueBar = new RectF();
    private final float[][] hsv = {{110f, .8f, .9f}, {250f, .8f, .9f}};
    private final float[][] before = new float[2][3];
    private Listener listener;
    private int activeStop = 0;
    private int tracking = AREA_NONE;

    public DualColorPickerView(Context context) { super(context); initialize(); }
    public DualColorPickerView(Context context, AttributeSet attrs) { super(context, attrs); initialize(); }

    private void initialize() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setFocusable(true);
        setContentDescription("Dégradé : deux curseurs de couleur 1 et 2 dans le carré et la barre arc-en-ciel");
    }
    private float dp(float value) { return Ui.dp(getContext(), value); }

    public void setColors(int first, int second) {
        Color.colorToHSV(first, hsv[0]);
        Color.colorToHSV(second, hsv[1]);
        invalidate();
    }
    public int getColor(int stop) { return Color.HSVToColor(hsv[stop == 1 ? 1 : 0]); }
    public int getActiveStop() { return activeStop; }
    public void setActiveStop(int stop) {
        activeStop = stop == 1 ? 1 : 0;
        invalidate();
    }
    public void setListener(Listener listener) { this.listener = listener; }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float inset = dp(13);
        shade.set(inset, dp(12), Math.max(inset + 1f, width - inset),
                Math.max(dp(13), height - dp(85)));
        hueBar.set(inset + dp(1), height - dp(49), Math.max(inset + dp(2), width - inset - dp(1)),
                height - dp(29));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float corner = dp(13);
        // The saturation square follows the selected stop's hue. Both
        // handles remain visible; each keeps its independent hue value.
        int vividHue = Color.HSVToColor(new float[]{hsv[activeStop][0], 1f, 1f});
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(shade.left, shade.top, shade.right, shade.top,
                Color.WHITE, vividHue, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(shade, corner, corner, paint);
        paint.setShader(new LinearGradient(shade.left, shade.top, shade.left, shade.bottom,
                Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(shade, corner, corner, paint);
        paint.setShader(null);

        paint.setColor(0xFF6B7888);
        paint.setStrokeWidth(dp(1));
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRoundRect(shade, corner, corner, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(hueBar.left, hueBar.top, hueBar.right, hueBar.top,
                RAINBOW, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(hueBar, dp(10), dp(10), paint);
        paint.setShader(null);

        // Draw the inactive marker first; the active one is always on top.
        drawStop(canvas, 1 - activeStop);
        drawStop(canvas, activeStop);
    }

    private void drawStop(Canvas canvas, int stop) {
        float sx = shade.left + shade.width() * hsv[stop][1];
        float sy = shade.top + shade.height() * (1f - hsv[stop][2]);
        drawMarker(canvas, sx, sy, stop, dp(13), getColor(stop));
        float hx = hueBar.left + hueBar.width() * hsv[stop][0] / 360f;
        drawMarker(canvas, hx, hueBar.centerY(), stop, dp(12),
                Color.HSVToColor(new float[]{hsv[stop][0],1f,1f}));
    }

    private void drawMarker(Canvas canvas, float x, float y, int stop, float radius, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(stop == activeStop ? Color.WHITE : 0xFF0C111A);
        canvas.drawCircle(x, y, radius + dp(3), paint);
        paint.setColor(color);
        canvas.drawCircle(x, y, radius, paint);
        paint.setColor(stop == activeStop ? 0xFF0C111A : Color.WHITE);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(12));
        canvas.drawText(stop == 0 ? "1" : "2", x, y + dp(4), paint);
    }

    private float distanceSquared(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private int closestStop(float x, float y, int area) {
        float[] measures = new float[2];
        for (int stop = 0; stop < 2; stop++) {
            float hx = area == AREA_HUE
                    ? hueBar.left + hueBar.width() * hsv[stop][0] / 360f
                    : shade.left + shade.width() * hsv[stop][1];
            float hy = area == AREA_HUE
                    ? hueBar.centerY()
                    : shade.top + shade.height() * (1f - hsv[stop][2]);
            measures[stop] = distanceSquared(x, y, hx, hy);
        }
        // A touch in the general palette edits the active stop, while a
        // nearby handle selects its own color. Overlaps retain the active one.
        return DualColorPickerRules.choose(activeStop,measures[0],measures[1],
                dp(31)*dp(31),dp(6)*dp(6));
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                float y = event.getY();
                if (y >= shade.top - dp(16) && y <= shade.bottom + dp(12))
                    tracking = AREA_SHADE;
                else if (y >= hueBar.top - dp(21) && y <= hueBar.bottom + dp(20))
                    tracking = AREA_HUE;
                else return false;
                for (int i = 0; i < 2; i++)
                    System.arraycopy(hsv[i], 0, before[i], 0, 3);
                activeStop = closestStop(event.getX(), y, tracking);
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                applyPosition(event.getX(), event.getY());
                notifyListener(false);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (tracking == AREA_NONE) return false;
                applyPosition(event.getX(), event.getY());
                notifyListener(false);
                return true;
            case MotionEvent.ACTION_UP:
                if (tracking == AREA_NONE) return false;
                applyPosition(event.getX(), event.getY());
                tracking = AREA_NONE;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                // Deferred callback prevents the editor/player from rebuilding
                // while Android is still dispatching this pointer event.
                final int first = getColor(0);
                final int second = getColor(1);
                final int selected = activeStop;
                post(() -> {
                    if (listener != null) listener.onColorsChanged(first, second, selected, true);
                });
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (tracking == AREA_NONE) return false;
                for (int i = 0; i < 2; i++)
                    System.arraycopy(before[i], 0, hsv[i], 0, 3);
                tracking = AREA_NONE;
                invalidate();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                notifyListener(false);
                return true;
            default:
                return false;
        }
    }

    private void applyPosition(float x, float y) {
        if (tracking == AREA_SHADE) {
            hsv[activeStop][1] = ColorPickerMath.saturation(x, shade.left, shade.right);
            hsv[activeStop][2] = ColorPickerMath.brightness(y, shade.top, shade.bottom);
        } else if (tracking == AREA_HUE) {
            hsv[activeStop][0] = ColorPickerMath.hue(x, hueBar.left, hueBar.right);
            if (hsv[activeStop][1] < .08f) hsv[activeStop][1] = .8f;
            if (hsv[activeStop][2] < .08f) hsv[activeStop][2] = .8f;
        }
        invalidate();
    }
    private void notifyListener(boolean finished) {
        if (listener != null)
            listener.onColorsChanged(getColor(0), getColor(1), activeStop, finished);
    }
    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
