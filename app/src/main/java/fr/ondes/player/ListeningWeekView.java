package fr.ondes.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Compact native chart showing the last 7 days of actual listening time. */
public final class ListeningWeekView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<LibraryStore.DailyStat> days;
    private final float density;
    private final float scaled;

    public ListeningWeekView(Context c, List<LibraryStore.DailyStat> values) {
        super(c);
        days = values;
        density = getResources().getDisplayMetrics().density;
        scaled = getResources().getDisplayMetrics().scaledDensity;
        setContentDescription("Graphique du temps d'écoute des sept derniers jours");
    }
    private float d(float x) { return x * density; }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dInt(174));
    }
    private int dInt(float n) { return Math.round(d(n)); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (days.isEmpty()) return;
        float w = getWidth();
        float top = d(28), bottom = getHeight() - d(33);
        float available = bottom - top;
        float step = w / 7f;
        long max = 1L;
        for (LibraryStore.DailyStat day : days) max = Math.max(max, day.listenedMs);
        // Provide visible vertical scale even for a single brief listen.
        max = Math.max(max, 10L * 60000L);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(d(1));
        paint.setColor(0x244E5961);
        for (int i = 0; i < 3; i++) {
            float y = top + available * i / 2f;
            canvas.drawLine(d(5), y, w - d(5), y, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        Calendar date = Calendar.getInstance(Locale.FRANCE);
        date.add(Calendar.DAY_OF_YEAR, -6);
        SimpleDateFormat fmt = new SimpleDateFormat("EEE", Locale.FRANCE);
        for (int i = 0; i < 7; i++) {
            LibraryStore.DailyStat data = days.get(i);
            float cx = step * (i + .5f);
            float bw = Math.min(d(25), step * .48f);
            float h = data.listenedMs == 0 ? d(3) : Math.max(d(7), (float) data.listenedMs / max * available);
            paint.setColor(data.listenedMs > 0 ? (i == 6 ? Ui.ACCENT : 0xFF8EA5DF) : 0xFF343A46);
            canvas.drawRoundRect(new RectF(cx - bw/2f, bottom - h, cx + bw/2f, bottom), d(6), d(6), paint);
            paint.setColor(i == 6 ? Ui.ACCENT : Ui.MUTED);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(10.5f * scaled);
            canvas.drawText(fmt.format(date.getTime()).replace(".", ""), cx, getHeight() - d(11), paint);
            if (data.listenedMs >= 60000L) {
                paint.setColor(Ui.TEXT);
                paint.setTextSize(10f * scaled);
                long minutes = data.listenedMs / 60000L;
                String label = minutes >= 60 ? (minutes / 60) + "h" : minutes + "m";
                canvas.drawText(label, cx, Math.max(d(13), bottom - h - d(6)), paint);
            }
            date.add(Calendar.DAY_OF_YEAR, 1);
        }
    }
}
