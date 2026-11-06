package fr.ondes.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.View;

/** A translucent, accessible coachmark scrim with a cutout over the target element. */
public final class GuideScrimView extends View {
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF focus = new RectF();
    public GuideScrimView(Context c) {
        super(c);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        shade.setColor(0xD910151E);
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        outline.setColor(Ui.ACCENT);
        outline.setStrokeWidth(Ui.dp(c, 2));
        outline.setStyle(Paint.Style.STROKE);
    }
    public void highlight(float left, float top, float right, float bottom) {
        int p = Ui.dp(getContext(), 5);
        focus.set(left-p, top-p, right+p, bottom+p);
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int save = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        canvas.drawRect(0, 0, getWidth(), getHeight(), shade);
        float radius = Ui.dp(getContext(), 15);
        canvas.drawRoundRect(focus, radius, radius, clear);
        canvas.restoreToCount(save);
        canvas.drawRoundRect(focus, radius, radius, outline);
    }
}
